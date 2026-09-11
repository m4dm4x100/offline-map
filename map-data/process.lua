-- Minimal offline map profile for India.
-- Only the geometry needed for an on-device basemap is emitted.
-- OSM data is processed at build time; the APK never contacts a map server.

node_keys = { "place", "natural", "waterway" }
way_keys = { "highway", "waterway", "natural", "landuse", "landcover" }

local roadZoom = {
  motorway = 4, trunk = 5, primary = 6, secondary = 7,
  tertiary = 8, unclassified = 9, residential = 9, living_street = 9,
  service = 10, track = 11, path = 11, footway = 11,
  cycleway = 11, pedestrian = 11
}

function node_function()
  local place = Find("place")
  if place ~= "" then
    Layer("place", false)
    Attribute("class", place)
    Attribute("name", Find("name"))
    MinZoom(5)
    return
  end
end

function way_function()
  local highway = Find("highway")
  if highway ~= "" then
    local mz = roadZoom[highway]
    if mz then
      Layer("transportation", false)
      Attribute("class", highway)
      MinZoom(mz)
      return
    end
  end

  local waterway = Find("waterway")
  if waterway ~= "" then
    Layer("waterway", false)
    Attribute("class", waterway)
    MinZoom(7)
    return
  end

  local natural = Find("natural")
  if natural == "water" or natural == "bay" then
    Layer("water", true)
    MinZoom(5)
    return
  end

  local landuse = Find("landuse")
  if landuse ~= "" then
    Layer("landuse", true)
    Attribute("class", landuse)
    MinZoom(7)
    return
  end

  local landcover = Find("landcover")
  if landcover ~= "" then
    Layer("landcover", true)
    Attribute("class", landcover)
    MinZoom(7)
  end
end
