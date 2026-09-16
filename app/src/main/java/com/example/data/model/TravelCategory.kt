package com.example.data.model

enum class TravelCategory(val displayName: String, val iconName: String) {
    ALL("All", "Explore"),
    HISTORICAL("Historical", "AccountBalance"),
    FORTS("Forts & Palaces", "Castle"),
    SPIRITUAL("Spiritual", "TempleHindu"),
    NATURE("Nature & Wildlife", "Forest"),
    BEACHES("Beaches & Coastal", "BeachAccess"),
    UNESCO("UNESCO Heritage", "Verified"),
    HILL_STATION("Hill Stations", "Landscape"),
    CULTURE("Culture & Cuisine", "Restaurant")
}
