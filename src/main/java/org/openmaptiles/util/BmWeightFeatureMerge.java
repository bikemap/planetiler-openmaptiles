package org.openmaptiles.util;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.GeometryPipeline;
import com.onthegomap.planetiler.geo.GeometryType;
import com.onthegomap.planetiler.geo.MutableCoordinateSequence;
import com.onthegomap.planetiler.util.LoopLineMerger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.openmaptiles.generated.OpenMapTilesSchema;


/**
 * A collection of utilities for merging features with the same attributes in a rendered tile from
 * Profile.postProcessLayerFeatures immediately before a tile is written to the output
 * archive.
 * <p>
 * Unlike postgis-based solutions that have a full view of all features after they are loaded into the database, the
 * planetiler engine only sees a single input feature at a time while processing source features, then only has
 * visibility into multiple features when they are grouped into a tile immediately before emitting. This ends up being
 * sufficient for most real-world use-cases but to do anything more that requires a view of multiple features
 * <em>not</em> within the same tile, Profile implementations must store input features manually.
 */
public class BmWeightFeatureMerge {

  // this is slightly faster than Comparator.comparingInt
  private static final Comparator<WithIndex<?>> BY_HILBERT_INDEX =
      (o1, o2) -> Integer.compare(o1.hilbert, o2.hilbert);

  /** Don't instantiate */
  private BmWeightFeatureMerge() {}

  /**
   * Modifies a feature ID to end in 0, indicating the feature was created by merging and doesn't correspond to a single
   * source element.
   * <p>
   * For OSM features, IDs are encoded as {@code osm_id * 10 + element_type} where element_type is 1 for nodes, 2 for
   * ways, and 3 for relations. IDs ending in 0 are reserved for features that don't map to a single OSM element.
   *
   * @param id the original feature ID
   * @return the modified ID ending in 0
   */
  private static long makeMergedId(long id) {
    return (id / 10) * 10;
  }

  /**
   * Combines linestrings with the same set of attributes into a multilinestring where segments with touching endpoints
   * are merged by {@link LineMerger}, removing any linestrings under {@code minLength}.
   * <p>
   * Ignores any non-linestrings and passes them through to the output unaltered.
   * <p>
   * Orders grouped output multilinestring by the index of the first element in that group from the input list.
   *
   * @param features   all features in a layer
   * @param minLength  minimum tile pixel length of features to emit, or 0 to emit all merged linestrings
   * @param tolerance  after merging, simplify linestrings using this pixel tolerance, or -1 to skip simplification step
   * @param buffer     number of pixels outside the visible tile area to include detail for, or -1 to skip clipping step
   * @param resimplify True if linestrings should be simplified even if they don't get merged with another
   * @return a new list containing all unaltered features in their original order, then each of the merged groups
   *         ordered by the index of the first element in that group from the input list.
   */
  public static List<VectorTile.Feature> mergeLineStrings(List<VectorTile.Feature> features,
      double minLength, double tolerance, double buffer, boolean resimplify) {
    return mergeLineStrings(features, attrs -> minLength, tolerance, buffer, resimplify, null);
  }

  /**
   * Merges linestrings with the same attributes as {@link #mergeLineStrings(List, double, double, double, boolean)}
   * except sets {@code resimplify=false} by default.
   */
  public static List<VectorTile.Feature> mergeLineStrings(List<VectorTile.Feature> features,
      double minLength, double tolerance, double buffer) {
    return mergeLineStrings(features, minLength, tolerance, buffer, false);
  }

  /**
   * Merges linestrings with the same attributes as {@link #mergeLineStrings(List, double, double, double, boolean)}
   * except with a dynamic length limit computed by {@code lengthLimitCalculator} for the attributes of each group.
   */
  public static List<VectorTile.Feature> mergeLineStrings(List<VectorTile.Feature> features,
      Function<Map<String, Object>, Double> lengthLimitCalculator, double tolerance, double buffer, boolean resimplify,
      GeometryPipeline pipeline) {
    List<VectorTile.Feature> result = new ArrayList<>(features.size());
    var groupedByAttrs = groupByAttrs(features, result, GeometryType.LINE);
    for (List<VectorTile.Feature> groupedFeatures : groupedByAttrs) {
      VectorTile.Feature feature1 = groupedFeatures.getFirst();
      double lengthLimit = lengthLimitCalculator.apply(feature1.tags());

      // as a shortcut, can skip line merging only if:
      // - only 1 element in the group
      // - it doesn't need to be clipped
      // - and it can't possibly be filtered out for being too short
      // - and it does not need to be simplified
      if (groupedFeatures.size() == 1 && buffer <= 0d && lengthLimit <= 0 && (!resimplify || tolerance <= 0)) {
        result.add(feature1);
      } else {
        long bmWeightTracked = 0L;
        LoopLineMerger merger = new LoopLineMerger()
            .setTolerance(tolerance)
            .setMergeStrokes(true)
            .setMinLength(lengthLimit)
            .setLoopMinLength(lengthLimit)
            .setStubMinLength(Math.min(0.5, lengthLimit))
            .setSegmentTransform(pipeline);
        for (VectorTile.Feature feature : groupedFeatures) {
          try {
            merger.add(feature.geometry().decode());
            long featureBmWeightTracked = feature.getLong(OpenMapTilesSchema.Transportation.Fields.BM_WEIGHT_TRACKED);
            if (featureBmWeightTracked != 0L) {
              bmWeightTracked = Math.max(bmWeightTracked, featureBmWeightTracked);
            }
          } catch (GeometryException e) {
            e.log("Error decoding vector tile feature for line merge: " + feature);
          }
        }
        List<LineString> outputSegments = new ArrayList<>();
        for (var line : merger.getMergedLineStrings()) {
          if (buffer >= 0) {
            removeDetailOutsideTile(line, buffer, outputSegments);
          } else {
            outputSegments.add(line);
          }
        }

        if (!outputSegments.isEmpty()) {
          outputSegments = sortByHilbertIndex(outputSegments);
          Geometry newGeometry = GeoUtils.combineLineStrings(outputSegments);
          if (groupedFeatures.size() > 1) {
            if (bmWeightTracked > 0L) {
              feature1.tags().put(OpenMapTilesSchema.Transportation.Fields.BM_WEIGHT_TRACKED, bmWeightTracked);
            }
            result.add(feature1.copyWithIdAndGeometry(makeMergedId(feature1.id()),
                VectorTile.encodeGeometry(newGeometry)));
          } else {
            result.add(feature1.copyWithNewGeometry(newGeometry));
          }
        }
      }
    }
    return result;
  }

  /**
   * Removes any segments from {@code input} where both the start and end are outside the tile boundary (plus {@code
   * buffer}) and puts the resulting segments into {@code output}.
   */
  private static void removeDetailOutsideTile(LineString input, double buffer, List<LineString> output) {
    MutableCoordinateSequence current = new MutableCoordinateSequence();
    CoordinateSequence seq = input.getCoordinateSequence();
    boolean wasIn = false;
    double min = -buffer, max = 256 + buffer;
    double x = seq.getX(0), y = seq.getY(0);
    Envelope env = new Envelope();
    Envelope outer = new Envelope(min, max, min, max);
    for (int i = 0; i < seq.size() - 1; i++) {
      double nextX = seq.getX(i + 1), nextY = seq.getY(i + 1);
      env.init(x, nextX, y, nextY);
      boolean nowIn = env.intersects(outer);
      if (nowIn || wasIn) {
        current.addPoint(x, y);
      } else { // out
        // wait to flush until 2 consecutive outs
        if (!current.isEmpty()) {
          if (current.size() >= 2) {
            output.add(GeoUtils.JTS_FACTORY.createLineString(current));
          }
          current = new MutableCoordinateSequence();
        }
      }
      wasIn = nowIn;
      x = nextX;
      y = nextY;
    }

    // last point
    double lastX = seq.getX(seq.size() - 1), lastY = seq.getY(seq.size() - 1);
    env.init(x, lastX, y, lastY);
    if (env.intersects(outer) || wasIn) {
      current.addPoint(lastX, lastY);
    }

    if (current.size() >= 2) {
      output.add(GeoUtils.JTS_FACTORY.createLineString(current));
    }
  }

  private static <G extends Geometry> List<G> sortByHilbertIndex(List<G> geometries) {
    return geometries.stream()
        .map(p -> new WithIndex<>(p, VectorTile.hilbertIndex(p)))
        .sorted(BY_HILBERT_INDEX)
        .map(d -> d.feature)
        .toList();
  }


  /**
   * Returns each group of vector tile features that share the exact same attributes.
   *
   * @param features     the set of input features
   * @param others       list to add any feature that does not match {@code geometryType}
   * @param geometryType the type of geometries to return in the result
   * @return all the elements from {@code features} of type {@code geometryType} grouped by attributes
   */
  public static Collection<List<VectorTile.Feature>> groupByAttrs(
      List<VectorTile.Feature> features,
      List<VectorTile.Feature> others,
      GeometryType geometryType
  ) {
    LinkedHashMap<Map<String, Object>, List<VectorTile.Feature>> groupedByAttrs = new LinkedHashMap<>();

    for (VectorTile.Feature feature : features) {
      if (feature == null) {
        // ignore
      } else if (feature.geometry().geomType() != geometryType) {
        // just ignore and pass through non-polygon features
        others.add(feature);
      } else {
        Object bmWeightTracked = feature.tags().remove(OpenMapTilesSchema.Transportation.Fields.BM_WEIGHT_TRACKED);
        List<VectorTile.Feature> group = groupedByAttrs.computeIfAbsent(feature.tags(), k -> new ArrayList<>());
        if (bmWeightTracked != null) {
          feature.tags().put(OpenMapTilesSchema.Transportation.Fields.BM_WEIGHT_TRACKED, bmWeightTracked);
        }
        group.add(feature);
      }
    }
    return groupedByAttrs.values();
  }

  private record WithIndex<T>(T feature, int hilbert) {}
}