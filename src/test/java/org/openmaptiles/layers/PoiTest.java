package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newPoint;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openmaptiles.OpenMapTilesProfile;

class PoiTest extends AbstractLayerTest {

  private SourceFeature feature(boolean area, Map<String, Object> tags) {
    return area ? polygonFeature(tags) : pointFeature(tags);
  }

  @Test
  void testFenwayPark() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "stadium",
      "subclass", "stadium",
      "name", "Fenway Park",
      "rank", "<null>",
      "_minzoom", 14,
      "_labelgrid_size", 64d
    )), process(pointFeature(Map.of(
      "leisure", "stadium",
      "name", "Fenway Park"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testFunicularHalt(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "railway",
      "subclass", "halt",
      "rank", "<null>",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "railway", "station",
      "funicular", "yes",
      "name", "station"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testSubway(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "railway",
      "subclass", "subway",
      "rank", "<null>",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "railway", "station",
      "station", "subway",
      "name", "station"
    ))));
  }

  private List<FeatureCollector.Feature> testAggStops(List<SourceFeature> sourceFeatures) {
    sourceFeatures.forEach(this::process);

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, featureCollectorFactory, features::add);

    return features;
  }

  @Test
  void testAggStopJustOne() {
    var result = testAggStops(List.of(pointFeature(Map.of(
      "highway", "bus_stop",
      "name", "station",
      "uic_ref", "1"
    ))));
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "bus",
      "subclass", "bus_stop",
      "agg_stop", 1,
      "_minzoom", 14
    )), result);
  }

  @Test
  void testAggStopTwoWithSameSubclass() {
    var result = testAggStops(List.of(
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      ))
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", 1,
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      )
    ), result);
  }

  @Test
  void testAggStopThreeWithMixedSubclass() {
    var result = testAggStops(List.of(
      pointFeature(Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      )),
      pointFeature(Map.of(
        "railway", "tram_stop",
        "name", "station",
        "name:es", "test 3",
        "uic_ref", "1"
      ))
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 3",
        "class", "railway",
        "subclass", "tram_stop",
        "agg_stop", 1,
        "_minzoom", 14
      )
    ), result);
  }

  @Test
  void testAggStopThreeWithSameSubclass() {
    var result = testAggStops(List.of(
      SimpleFeature.create(newPoint(0, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 1",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 0),
      SimpleFeature.create(newPoint(1, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 2",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 1),
      SimpleFeature.create(newPoint(2, 0), Map.of(
        "highway", "bus_stop",
        "name", "station",
        "name:es", "test 3",
        "uic_ref", "1"
      ), OpenMapTilesProfile.OSM_SOURCE, null, 2)
    ));
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "name:es", "test 1",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 2",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", 1,
        "_minzoom", 14
      ),
      Map.of(
        "_layer", "poi",
        "name:es", "test 3",
        "class", "bus",
        "subclass", "bus_stop",
        "agg_stop", "<null>",
        "_minzoom", 14
      )
    ), result);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testPlaceOfWorshipFromReligionTag(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "place_of_worship",
      "subclass", "religion value",
      "rank", "<null>",
      "_minzoom", 14
    )), process(feature(area, Map.of(
      "amenity", "place_of_worship",
      "religion", "religion value",
      "name", "station"
    ))));
  }

  @Test
  void testPitchFromSportTag() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "pitch",
      "subclass", "soccer",
      "rank", "<null>"
    )), process(pointFeature(Map.of(
      "leisure", "pitch",
      "sport", "soccer",
      "name", "station"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testInformation(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "information",
      "subclass", "infotype",
      "layer", 3L,
      "level", 2L,
      "indoor", 1,
      "rank", "<null>"
    )), process(feature(area, Map.of(
      "tourism", "information",
      "information", "infotype",
      "name", "station",
      "layer", "3",
      "level", "2",
      "indoor", "yes"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testFerryTerminal(boolean area) {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "ferry_terminal",
      "subclass", "ferry_terminal",
      "name", "Water Taxi",
      "_minzoom", 12
    )), process(feature(area, Map.of(
      "amenity", "ferry_terminal",
      "information", "infotype",
      "name", "Water Taxi",
      "layer", "3",
      "level", "2",
      "indoor", "yes"
    ))));
  }

  @Test
  void testGridRank() throws GeometryException {
    var layerName = Poi.LAYER_NAME;
    Assertions.assertEquals(List.of(), profile.postProcessLayerFeatures(layerName, 13, List.of()));

    Assertions.assertEquals(List.of(pointFeature(
      layerName,
      Map.of("rank", 1),
      1
    )), profile.postProcessLayerFeatures(layerName, 14, List.of(pointFeature(
      layerName,
      Map.of(),
      1
    ))));

    Assertions.assertEquals(List.of(
      pointFeature(
        layerName,
        Map.of("rank", 1, "name", "a"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 2, "name", "b"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 1, "name", "c"),
        2
      )
    ), profile.postProcessLayerFeatures(layerName, 14, List.of(
      pointFeature(
        layerName,
        Map.of("name", "a"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "b"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "c"),
        2
      )
    )));
  }

  @Test
  void testEmbassy() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "office",
      "subclass", "diplomatic",
      "name", "The Embassy"
    )), process(pointFeature(Map.of(
      "office", "diplomatic",
      "name", "The Embassy"
    ))));
  }

  @Test
  void testLocksmith() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "poi",
      "class", "shop",
      "subclass", "locksmith",
      "name", "The Locksmith"
    )), process(pointFeature(Map.of(
      "shop", "locksmith",
      "name", "The Locksmith"
    ))));
  }

  @Test
  void testAtm() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "atm",
      "subclass", "atm",
      "name", "ATM name"
    ));
    // prefer name, otherwise fall back to operator, or else network
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "name", "ATM name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "name", "ATM name",
      "operator", "ATM operator",
      "network", "ATM network"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "operator", "ATM name",
      "network", "ATM network"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "atm",
      "network", "ATM name"
    ))));
  }

  @Test
  void testParcelLocker() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "post",
      "subclass", "parcel_locker",
      "name", "Parcel Locker name"
    ));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "brand", "Parcel Locker name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "operator", "Parcel Locker name"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "operator", "Parcel Locker",
      "ref", "name"
    ))));
  }

  @Test
  void testParcelLockerCornerCase() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "post",
      "subclass", "parcel_locker",
      "name", "Corner Case"
    ));
    // no brand, no operator, just ref
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "parcel_locker",
      "ref", "Corner Case"
    ))));
  }

  @Test
  void testChargingStation() {
    List<Map<String, Object>> expected = List.of(Map.of(
      "_layer", "poi",
      "class", "fuel",
      "subclass", "charging_station",
      "name", "Some Charging Station Operator"
    ));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "brand", "Some Charging Station Operator"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "operator", "Some Charging Station Operator"
    ))));
    assertFeatures(14, expected, process(pointFeature(Map.of(
      "amenity", "charging_station",
      "operator", "Some Charging Station",
      "ref", "Operator"
    ))));
  }

  private void assertCategory(String category, Map<String, Object> tags) {
    var features = process(pointFeature(tags));
    assertFeatures(14, List.of(Map.of("_layer", "poi", "category", category == null ? "<null>" : category)), features);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testBicycleRepairStationCategory(boolean area) {
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "bicycle_repair_station",
      "subclass", "bicycle_repair_station",
      "category", "pump"
    )), process(feature(area, Map.of(
      "amenity", "bicycle_repair_station",
      "service:bicycle:pump", "yes"
    ))));
    assertCategory("pump", Map.of(
      "amenity", "bicycle_repair_station",
      "service:bicycle:pump", "yes",
      "service:bicycle:tools", "no"
    ));
    assertCategory("self-service-station", Map.of(
      "amenity", "bicycle_repair_station",
      "service:bicycle:pump", "yes",
      "service:bicycle:tools", "yes"
    ));
    assertCategory("self-service-station", Map.of(
      "amenity", "bicycle_repair_station",
      "service:bicycle:tools", "yes"
    ));
    // neither service tag: the usual mapping of a repair stand
    assertCategory("self-service-station", Map.of("amenity", "bicycle_repair_station"));
  }

  @Test
  void testPrivateAccessHasNoCategory() {
    assertCategory("bike-parking", Map.of("amenity", "bicycle_parking"));
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "subclass", "bicycle_parking",
      "category", "<null>"
    )), process(pointFeature(Map.of(
      "amenity", "bicycle_parking",
      "access", "private"
    ))));
    assertCategory(null, Map.of("amenity", "drinking_water", "access", "no"));
    assertCategory("drinking-water", Map.of("amenity", "drinking_water", "access", "customers"));
  }

  @Test
  void testVendingMachineCategory() {
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "vending_machine",
      "subclass", "vending_machine",
      "category", "bicycle-tubes"
    )), process(pointFeature(Map.of(
      "amenity", "vending_machine",
      "vending", "drinks; bicycle_tube"
    ))));
    assertCategory("bicycle-tubes", Map.of("amenity", "vending_machine", "vending", "bicycle_tube"));
    // the mapping only checks for the substring; process() drops the near misses
    assertFeatures(14, List.of(),
      process(pointFeature(Map.of("amenity", "vending_machine", "vending", "bicycle_tubes"))));
    assertFeatures(14, List.of(),
      process(polygonFeature(Map.of("amenity", "vending_machine", "vending", "drinks;bicycle_tube_kit"))));
    assertFeatures(14, List.of(), process(pointFeature(Map.of("amenity", "vending_machine", "vending", "cigarettes"))));
    assertFeatures(14, List.of(), process(pointFeature(Map.of("amenity", "vending_machine"))));
  }

  @Test
  void testSwimmingLake() {
    assertFeatures(14, List.of(
      Map.of(
        "_layer", "poi",
        "class", "water",
        "subclass", "water",
        "category", "swimming-spot"
      ),
      Map.of("_layer", "water")
    ), process(polygonFeature(Map.of(
      "natural", "water",
      "swimming", "yes"
    ))));
    assertFeatures(14, List.of(Map.of("_layer", "water")), process(polygonFeature(Map.of("natural", "water"))));
  }

  @Test
  void testInformationCategory() {
    assertCategory("info-point", Map.of("tourism", "information", "information", "board"));
    assertCategory("info-point", Map.of("tourism", "information", "information", "office"));
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "information",
      "subclass", "guidepost",
      "category", "<null>"
    )), process(pointFeature(Map.of(
      "tourism", "information",
      "information", "guidepost"
    ))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testDefibrillator(boolean area) {
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "defibrillator",
      "subclass", "defibrillator",
      "category", "aed"
    )), process(feature(area, Map.of("emergency", "defibrillator"))));
  }

  @Test
  void testOtherCategories() {
    assertCategory("e-bike-charging", Map.of("amenity", "charging_station", "bicycle", "yes"));
    assertCategory(null, Map.of("amenity", "charging_station"));
    assertCategory("shelter", Map.of("amenity", "shelter", "shelter_type", "basic_hut"));
    assertCategory(null, Map.of("amenity", "shelter", "shelter_type", "public_transport"));
    assertCategory("public-toilets", Map.of("amenity", "toilets"));
    assertCategory("swimming-spot", Map.of("leisure", "swimming_area"));
    assertCategory("playground", Map.of("leisure", "playground"));
    assertCategory("picnic", Map.of("tourism", "picnic_site"));
    assertCategory("campsite", Map.of("tourism", "camp_site"));
    assertCategory("campsite", Map.of("tourism", "caravan_site"));
    assertCategory("vista-point", Map.of("tourism", "viewpoint"));
    assertCategory("repair-shop", Map.of("shop", "bicycle"));
    // the first rule wins: a repair station inside a bike shop is a station
    assertCategory("self-service-station", Map.of("amenity", "bicycle_repair_station", "shop", "bicycle"));
  }

  @Test
  void testMapOnlyAmenitiesHaveNoCategory() {
    assertFeatures(14, List.of(Map.of("_layer", "poi", "subclass", "bench", "category", "<null>")),
      process(pointFeature(Map.of("amenity", "bench"))));
    assertFeatures(14, List.of(Map.of("_layer", "poi", "subclass", "shower", "category", "<null>")),
      process(pointFeature(Map.of("amenity", "shower"))));
  }

  @Test
  void testNameTrunc() {
    String name45 = "Fahrradservicestation am Hauptbahnhof Westsei";
    Assertions.assertEquals(45, name45.length());
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "name", name45,
      "name_trunc", "Fahrradservicestation am Hauptbahnhof W…"
    )), process(pointFeature(Map.of("amenity", "bicycle_repair_station", "name", name45))));

    String name40 = "Fahrradservicestation am Hauptbahnhof We";
    Assertions.assertEquals(40, name40.length());
    Assertions.assertEquals(name40, Poi.truncatedName(name40));
    // code points, not UTF-16 units: the emoji counts once and is never cut in half
    Assertions.assertEquals("\uD83D\uDEB2".repeat(39) + "…", Poi.truncatedName("\uD83D\uDEB2".repeat(41)));
    Assertions.assertEquals("\uD83D\uDEB2".repeat(40), Poi.truncatedName("\uD83D\uDEB2".repeat(40)));

    assertFeatures(14, List.of(Map.of("_layer", "poi", "name_trunc", "<null>")),
      process(pointFeature(Map.of("amenity", "bicycle_repair_station"))));
  }

  @Test
  void testNameTruncLikeDjango() {
    // expected values from Django 5.2 truncatechars(name, 40)
    String decomposed = "Cafe\u0301 E\u0301lyse\u0301e Rho\u0302ne-Alpes " +
      "Be\u0301gue\u0301 Che\u0302ne\u0301e Ho\u0302tel He\u0301rault";
    Assertions.assertEquals(
      "Caf\u00E9 \u00C9lys\u00E9e Rh\u00F4ne-Alpes B\u00E9gu\u00E9 Ch\u00EAn\u00E9e H\u00F4\u2026",
      Poi.truncatedName(decomposed));
    Assertions.assertEquals("Caf\u00E9 \u00C9lys\u00E9e", Poi.truncatedName("Cafe\u0301 E\u0301lyse\u0301e"));

    // 52 code points, 45 of them with combining class 0; the tone mark stays on the last kept letter
    String thai = "\u0E08\u0E38\u0E14\u0E0A\u0E21\u0E27\u0E34\u0E27\u0E19\u0E49\u0E33\u0E15\u0E01" +
      "\u0E40\u0E08\u0E47\u0E14\u0E2A\u0E32\u0E27\u0E19\u0E49\u0E2D\u0E22\u0E2D\u0E38\u0E17" +
      "\u0E22\u0E32\u0E19\u0E41\u0E2B\u0E48\u0E07\u0E0A\u0E32\u0E15\u0E34\u0E40\u0E02\u0E32" +
      "\u0E43\u0E2B\u0E0D\u0E48\u0E2A\u0E23\u0E30\u0E1A\u0E38\u0E23\u0E35";
    Assertions.assertEquals(thai.substring(0, 45) + "\u2026", Poi.truncatedName(thai));
  }

  @Test
  void testNewBranchesDoNotTakeOverExistingClasses() {
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "bus",
      "subclass", "bus_stop"
    )), process(pointFeature(Map.of("highway", "bus_stop", "amenity", "bench", "name", "Hauptplatz"))));
    var aggStop = testAggStops(List.of(pointFeature(Map.of(
      "highway", "bus_stop",
      "amenity", "bench",
      "name", "Hauptplatz",
      "uic_ref", "1"
    ))));
    assertFeatures(14, List.of(Map.of("_layer", "poi", "subclass", "bus_stop", "agg_stop", 1)), aggStop);

    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "railway",
      "subclass", "station",
      "category", "aed",
      "_minzoom", 12
    )), process(pointFeature(Map.of("railway", "station", "emergency", "defibrillator", "name", "Hauptbahnhof"))));

    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "attraction",
      "subclass", "viewpoint",
      "category", "vista-point"
    )), process(pointFeature(Map.of("tourism", "viewpoint", "amenity", "bench"))));
  }

  @Test
  void testMapOnlyClassesRankLast() {
    var drinkingWater = process(pointFeature(Map.of("amenity", "drinking_water"))).iterator().next();
    var namedBench = process(pointFeature(Map.of("amenity", "bench", "name", "Bank"))).iterator().next();
    var bench = process(pointFeature(Map.of("amenity", "bench"))).iterator().next();
    // same point, so the same label grid cell: the lower sort key takes rank 1
    Assertions.assertTrue(drinkingWater.getSortKey() < namedBench.getSortKey());
    Assertions.assertTrue(namedBench.getSortKey() < bench.getSortKey());
    Assertions.assertTrue(Poi.poiClassRank("bicycle_repair_station") < Poi.poiClassRank("bench"));
    // community report categories keep the default rank
    var defibrillator = process(pointFeature(Map.of("emergency", "defibrillator"))).iterator().next();
    Assertions.assertEquals(drinkingWater.getSortKey(), defibrillator.getSortKey());
  }

  @ParameterizedTest
  @CsvSource({
    "picnic_shelter, shelter",
    "basic_hut, shelter",
    "weather_shelter, shelter",
    "public_transport,",
    "lean_to,",
    "pavilion,",
    "rock_shelter,",
    "sun_shelter,",
    "field_shelter,",
    "gazebo,",
    "changing_rooms,",
  })
  void testShelterTypes(String shelterType, String category) {
    assertCategory(category, Map.of("amenity", "shelter", "shelter_type", shelterType));
  }

  @ParameterizedTest
  @CsvSource({
    "board, info-point",
    "map, info-point",
    "office, info-point",
    "guidepost,",
    "route_marker,",
    "terminal,",
    "tactile_map,",
    "visitor_centre,",
    "audioguide,",
  })
  void testInformationTypes(String information, String category) {
    assertCategory(category, Map.of("tourism", "information", "information", information));
  }

  @Test
  void testRuleOrder() {
    assertCategory(null, Map.of("amenity", "shelter"));
    assertCategory(null, Map.of("tourism", "information"));
    assertCategory("public-toilets", Map.of("amenity", "toilets", "emergency", "defibrillator"));
    assertCategory("drinking-water", Map.of("amenity", "drinking_water", "emergency", "defibrillator"));
    assertCategory("aed", Map.of("emergency", "defibrillator", "leisure", "playground"));
    assertCategory("playground", Map.of("leisure", "playground", "tourism", "picnic_site"));
    assertCategory("picnic", Map.of("tourism", "picnic_site", "shop", "bicycle"));
    assertCategory("bike-parking", Map.of("amenity", "bicycle_parking", "shop", "bicycle"));
  }

  @ParameterizedTest
  @CsvSource({
    "amenity, bicycle_repair_station, self-service-station",
    "amenity, bicycle_parking, bike-parking",
    "amenity, drinking_water, drinking-water",
    "amenity, toilets, public-toilets",
    "emergency, defibrillator, aed",
    "leisure, playground, playground",
    "tourism, camp_site, campsite",
    "tourism, viewpoint, vista-point",
    "shop, bicycle, repair-shop",
  })
  void testAccess(String key, String value, String category) {
    assertCategory(null, Map.of(key, value, "access", "private"));
    assertCategory(null, Map.of(key, value, "access", "no"));
    assertCategory(category, Map.of(key, value, "access", "yes"));
    assertCategory(category, Map.of(key, value, "access", "customers"));
    assertCategory(category, Map.of(key, value, "access", "permissive"));
    assertCategory(category, Map.of(key, value));
  }

  @Test
  void testRestaurantUnchanged() {
    assertFeatures(14, List.of(Map.of(
      "_layer", "poi",
      "class", "restaurant",
      "subclass", "restaurant",
      "name", "Zum Radler",
      "name_trunc", "Zum Radler",
      "category", "<null>",
      "rank", "<null>",
      "_minzoom", 14
    )), process(pointFeature(Map.of(
      "amenity", "restaurant",
      "name", "Zum Radler"
    ))));
  }

}
