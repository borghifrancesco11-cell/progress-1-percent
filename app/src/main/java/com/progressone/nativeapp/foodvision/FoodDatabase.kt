package com.progressone.nativeapp.foodvision

import kotlin.math.roundToInt

/**
 * Standard reference nutritional values per 100g and typical portion sizes
 * for Food-101 dishes (USDA / nutritional database averages), plus health score (0-100)
 * and intelligent rating calculation based on balance of macros, caloric density, and quality.
 */
data class FoodDishInfo(
    val id: String,
    val displayName: String,
    val defaultServingGrams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val category: String, // e.g. "Main Dish", "Dessert", "Salad", "Appetizer"
    val healthScore: Int,  // 0 - 100 base score
    val tip: String
)

data class NutritionalScoreResult(
    val score: Int,             // 0 - 100
    val grade: String,          // A+, A, B, C, D
    val feedback: String,
    val calorieLevel: String,   // Light, Moderate, High, Very High
    val proteinQuality: String  // Low, Moderate, High
)

object FoodDatabase {

    fun calculateIntelligentScore(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        portionGrams: Double,
        baseScore: Int? = null
    ): NutritionalScoreResult {
        // Evaluate nutritional density and balance
        // Grams per 100g equivalent
        val g = portionGrams.coerceAtLeast(1.0)
        val p100 = (protein / g) * 100.0
        val c100 = (carbs / g) * 100.0
        val f100 = (fat / g) * 100.0
        val k100 = (kcal / g) * 100.0

        var score = (baseScore ?: 65).toDouble()

        // Calorie density penalty/bonus
        when {
            k100 < 120 -> score += 10.0 // Nutrient-rich, low energy density
            k100 in 120.0..250.0 -> score += 5.0
            k100 in 250.0..400.0 -> score -= 5.0
            k100 > 400.0 -> score -= 15.0 // Very high energy density
        }

        // Protein bonus (satiety and muscle repair)
        when {
            p100 >= 18.0 -> score += 15.0
            p100 >= 10.0 -> score += 8.0
            p100 < 3.0 -> score -= 5.0
        }

        // Fat balance
        when {
            f100 > 25.0 -> score -= 12.0
            f100 in 3.0..15.0 -> score += 5.0
        }

        // Portion size penalty if excessive calories in one meal
        if (kcal > 850) score -= 10.0
        else if (kcal in 350.0..650.0) score += 5.0

        val finalScore = score.roundToInt().coerceIn(10, 100)

        val grade = when {
            finalScore >= 85 -> "A+"
            finalScore >= 75 -> "A"
            finalScore >= 60 -> "B"
            finalScore >= 45 -> "C"
            else -> "D"
        }

        val calLevel = when {
            kcal < 300 -> "Light"
            kcal <= 650 -> "Balanced"
            kcal <= 900 -> "Substantial"
            else -> "Heavy"
        }

        val pQuality = when {
            protein >= 25.0 -> "High Protein"
            protein >= 12.0 -> "Moderate Protein"
            else -> "Low Protein"
        }

        val feedback = when (grade) {
            "A+", "A" -> "Excellent nutritional profile with strong satiety and balance."
            "B" -> "Good balanced meal. Fits well into a healthy daily routine."
            "C" -> "Moderate nutrient density; enjoy in moderation alongside greens."
            else -> "High calorie or fat density; balance with lighter meals today."
        }

        return NutritionalScoreResult(
            score = finalScore,
            grade = grade,
            feedback = feedback,
            calorieLevel = calLevel,
            proteinQuality = pQuality
        )
    }

    private val dishes = mapOf(
        "apple_pie" to FoodDishInfo("apple_pie", "Apple Pie", 125.0, 237.0, 1.9, 34.0, 11.0, "Dessert", 42, "High sugar dessert; balance with light meals."),
        "baby_back_ribs" to FoodDishInfo("baby_back_ribs", "Baby Back Ribs", 250.0, 292.0, 24.0, 3.0, 21.0, "Main Dish", 52, "High protein but rich in saturated fat."),
        "baklava" to FoodDishInfo("baklava", "Baklava", 80.0, 428.0, 6.7, 51.0, 23.0, "Dessert", 35, "Very rich in honey and nuts, high energy density."),
        "beef_carpaccio" to FoodDishInfo("beef_carpaccio", "Beef Carpaccio", 150.0, 155.0, 22.0, 1.0, 7.0, "Appetizer", 82, "Lean protein, low carbohydrate."),
        "beef_tartare" to FoodDishInfo("beef_tartare", "Beef Tartare", 180.0, 188.0, 20.0, 2.0, 11.0, "Main Dish", 78, "High quality lean red meat."),
        "beet_salad" to FoodDishInfo("beet_salad", "Beet Salad", 200.0, 85.0, 2.5, 11.0, 4.0, "Salad", 90, "Rich in nitrates, fiber and antioxidants."),
        "beignets" to FoodDishInfo("beignets", "Beignets", 100.0, 360.0, 6.0, 45.0, 18.0, "Dessert", 32, "Deep-fried pastry with powdered sugar."),
        "bibimbap" to FoodDishInfo("bibimbap", "Bibimbap", 350.0, 142.0, 6.8, 21.0, 3.8, "Main Dish", 85, "Great combination of vegetables, egg, rice and meat."),
        "bread_pudding" to FoodDishInfo("bread_pudding", "Bread Pudding", 150.0, 240.0, 5.0, 38.0, 8.0, "Dessert", 45, "Carb-dense traditional pudding."),
        "breakfast_burrito" to FoodDishInfo("breakfast_burrito", "Breakfast Burrito", 220.0, 225.0, 11.0, 22.0, 10.0, "Main Dish", 68, "Provides steady morning energy with eggs and tortilla."),
        "bruschetta" to FoodDishInfo("bruschetta", "Bruschetta", 120.0, 160.0, 4.0, 22.0, 6.5, "Appetizer", 75, "Tomatoes provide lycopene with heart-healthy olive oil."),
        "caesar_salad" to FoodDishInfo("caesar_salad", "Caesar Salad", 220.0, 180.0, 5.5, 8.0, 15.0, "Salad", 64, "Fresh greens; dressing accounts for most fat."),
        "cannoli" to FoodDishInfo("cannoli", "Cannoli", 100.0, 310.0, 6.0, 34.0, 17.0, "Dessert", 40, "Ricotta provides some protein, sweet fried shell."),
        "caprese_salad" to FoodDishInfo("caprese_salad", "Caprese Salad", 200.0, 170.0, 9.0, 4.0, 13.0, "Salad", 82, "Rich in calcium, antioxidants and healthy fats."),
        "carrot_cake" to FoodDishInfo("carrot_cake", "Carrot Cake", 130.0, 350.0, 4.0, 48.0, 16.5, "Dessert", 38, "Dense spiced cake with frosting."),
        "ceviche" to FoodDishInfo("ceviche", "Ceviche", 200.0, 95.0, 14.5, 5.0, 1.8, "Main Dish", 92, "Lean marinated seafood, very high protein efficiency."),
        "cheesecake" to FoodDishInfo("cheesecake", "Cheesecake", 120.0, 321.0, 5.5, 25.5, 22.5, "Dessert", 36, "Rich cream cheese dessert."),
        "cheese_plate" to FoodDishInfo("cheese_plate", "Cheese Plate", 150.0, 380.0, 23.0, 2.0, 31.0, "Appetizer", 58, "High in calcium and protein, but high saturated fat."),
        "chicken_curry" to FoodDishInfo("chicken_curry", "Chicken Curry", 300.0, 145.0, 13.0, 7.5, 7.5, "Main Dish", 80, "Turmeric and spices boost anti-inflammatory benefits."),
        "chicken_quesadilla" to FoodDishInfo("chicken_quesadilla", "Chicken Quesadilla", 220.0, 245.0, 15.0, 20.0, 12.0, "Main Dish", 67, "Good protein content from chicken and cheese."),
        "chicken_wings" to FoodDishInfo("chicken_wings", "Chicken Wings", 200.0, 280.0, 22.0, 3.0, 20.0, "Main Dish", 55, "High protein, moderately high fat."),
        "chocolate_cake" to FoodDishInfo("chocolate_cake", "Chocolate Cake", 120.0, 370.0, 4.5, 52.0, 17.0, "Dessert", 32, "High sugar and fat indulgence."),
        "chocolate_mousse" to FoodDishInfo("chocolate_mousse", "Chocolate Mousse", 100.0, 320.0, 4.8, 30.0, 21.0, "Dessert", 38, "Decadent aerated chocolate dessert."),
        "churros" to FoodDishInfo("churros", "Churros", 100.0, 380.0, 4.5, 45.0, 21.0, "Dessert", 30, "Fried dough coated in sugar."),
        "clam_chowder" to FoodDishInfo("clam_chowder", "Clam Chowder", 250.0, 115.0, 5.0, 10.0, 6.0, "Soup", 68, "Creamy soup with iron-rich clams."),
        "club_sandwich" to FoodDishInfo("club_sandwich", "Club Sandwich", 240.0, 230.0, 13.0, 21.0, 11.0, "Main Dish", 70, "Balanced carbs and poultry protein."),
        "crab_cakes" to FoodDishInfo("crab_cakes", "Crab Cakes", 160.0, 185.0, 14.0, 12.0, 9.0, "Main Dish", 76, "Seafood protein with light breading."),
        "creme_brulee" to FoodDishInfo("creme_brulee", "Creme Brulee", 120.0, 330.0, 4.2, 26.0, 24.0, "Dessert", 34, "Rich custard with caramelized sugar top."),
        "croque_madame" to FoodDishInfo("croque_madame", "Croque Madame", 220.0, 275.0, 14.0, 22.0, 15.0, "Main Dish", 62, "Ham and cheese toast with fried egg."),
        "cup_cakes" to FoodDishInfo("cup_cakes", "Cupcakes", 90.0, 385.0, 3.5, 55.0, 17.5, "Dessert", 30, "Sweet baked goods."),
        "deviled_eggs" to FoodDishInfo("deviled_eggs", "Deviled Eggs", 120.0, 195.0, 11.0, 2.0, 16.0, "Appetizer", 72, "High satiety and choline from egg yolks."),
        "donuts" to FoodDishInfo("donuts", "Donuts", 80.0, 420.0, 5.0, 50.0, 22.0, "Dessert", 28, "Refined flour, sugar, and frying oil."),
        "dumplings" to FoodDishInfo("dumplings", "Dumplings", 200.0, 180.0, 9.0, 23.0, 6.0, "Main Dish", 74, "Steamed variety is lean and filling."),
        "edamame" to FoodDishInfo("edamame", "Edamame", 150.0, 122.0, 11.0, 9.0, 5.0, "Appetizer", 94, "Superb plant protein, dietary fiber, and micronutrients."),
        "eggs_benedict" to FoodDishInfo("eggs_benedict", "Eggs Benedict", 220.0, 255.0, 12.0, 15.0, 17.0, "Main Dish", 65, "Poached egg protein paired with rich hollandaise."),
        "escargots" to FoodDishInfo("escargots", "Escargots", 120.0, 170.0, 16.0, 3.0, 11.0, "Appetizer", 72, "High protein, flavored with garlic butter."),
        "falafel" to FoodDishInfo("falafel", "Falafel", 150.0, 280.0, 10.0, 31.0, 14.0, "Main Dish", 75, "Plant protein and fiber from chickpeas."),
        "filet_mignon" to FoodDishInfo("filet_mignon", "Filet Mignon", 200.0, 215.0, 26.0, 0.0, 12.5, "Main Dish", 85, "Tender, high quality protein and iron."),
        "fish_and_chips" to FoodDishInfo("fish_and_chips", "Fish and Chips", 300.0, 230.0, 11.0, 22.0, 11.0, "Main Dish", 58, "Fried fish provides omega-3s, but batter absorbs oil."),
        "foie_gras" to FoodDishInfo("foie_gras", "Foie Gras", 80.0, 460.0, 7.0, 4.0, 44.0, "Appetizer", 35, "Extremely high fat density."),
        "french_fries" to FoodDishInfo("french_fries", "French Fries", 150.0, 312.0, 3.4, 41.0, 15.0, "Side Dish", 45, "Crispy fried potatoes; limit portion size."),
        "french_onion_soup" to FoodDishInfo("french_onion_soup", "French Onion Soup", 250.0, 95.0, 4.5, 9.0, 4.5, "Soup", 75, "Rich onion broth with melted cheese."),
        "french_toast" to FoodDishInfo("french_toast", "French Toast", 160.0, 225.0, 7.0, 30.0, 8.5, "Breakfast", 60, "Egg-soaked bread; choose fresh fruit toppings."),
        "fried_calamari" to FoodDishInfo("fried_calamari", "Fried Calamari", 180.0, 210.0, 15.0, 13.0, 11.0, "Appetizer", 65, "Lean squid with crispy coating."),
        "fried_rice" to FoodDishInfo("fried_rice", "Fried Rice", 250.0, 175.0, 5.0, 28.0, 5.0, "Main Dish", 68, "Good source of energy; add vegetables and protein."),
        "frozen_yogurt" to FoodDishInfo("frozen_yogurt", "Frozen Yogurt", 150.0, 140.0, 3.5, 24.0, 3.0, "Dessert", 65, "Lighter alternative to regular ice cream."),
        "garlic_bread" to FoodDishInfo("garlic_bread", "Garlic Bread", 100.0, 350.0, 8.0, 42.0, 17.0, "Side Dish", 50, "Energy rich with savory garlic butter."),
        "gnocchi" to FoodDishInfo("gnocchi", "Gnocchi", 220.0, 175.0, 4.0, 36.0, 1.5, "Main Dish", 72, "Comforting potato dumplings, low in fat."),
        "greek_salad" to FoodDishInfo("greek_salad", "Greek Salad", 220.0, 110.0, 3.5, 5.0, 9.0, "Salad", 90, "Heart-healthy olive oil, crisp vegetables, and feta."),
        "grilled_cheese_sandwich" to FoodDishInfo("grilled_cheese_sandwich", "Grilled Cheese", 150.0, 330.0, 12.0, 31.0, 18.0, "Main Dish", 58, "Comfort food; contains calcium and carbohydrates."),
        "grilled_salmon" to FoodDishInfo("grilled_salmon", "Grilled Salmon", 200.0, 182.0, 25.0, 0.0, 9.0, "Main Dish", 96, "Superfood rich in omega-3 fatty acids and complete protein."),
        "guacamole" to FoodDishInfo("guacamole", "Guacamole", 100.0, 160.0, 2.0, 9.0, 14.5, "Side Dish", 88, "Monounsaturated fats and potassium from avocado."),
        "gyoza" to FoodDishInfo("gyoza", "Gyoza", 180.0, 190.0, 8.5, 21.0, 8.0, "Appetizer", 73, "Japanese dumplings with pork and cabbage."),
        "hamburger" to FoodDishInfo("hamburger", "Hamburger", 220.0, 250.0, 15.0, 24.0, 11.0, "Main Dish", 65, "Good protein source; watch heavy sauces."),
        "hot_and_sour_soup" to FoodDishInfo("hot_and_sour_soup", "Hot and Sour Soup", 250.0, 65.0, 4.0, 6.0, 2.8, "Soup", 84, "Low-calorie warming soup with mushrooms and tofu."),
        "hot_dog" to FoodDishInfo("hot_dog", "Hot Dog", 120.0, 290.0, 10.0, 24.0, 17.0, "Main Dish", 48, "Processed meat; best kept as an occasional treat."),
        "huevos_rancheros" to FoodDishInfo("huevos_rancheros", "Huevos Rancheros", 250.0, 160.0, 8.5, 14.0, 8.0, "Main Dish", 78, "Eggs, salsa, and beans offer balanced nutrients."),
        "hummus" to FoodDishInfo("hummus", "Hummus", 100.0, 170.0, 7.9, 14.0, 9.6, "Side Dish", 90, "Chickpeas and tahini provide fiber, iron, and good fats."),
        "ice_cream" to FoodDishInfo("ice_cream", "Ice Cream", 120.0, 210.0, 3.5, 24.0, 11.0, "Dessert", 45, "Calcium source with high sugar content."),
        "lasagna" to FoodDishInfo("lasagna", "Lasagna", 300.0, 155.0, 9.5, 15.0, 6.8, "Main Dish", 72, "Layered pasta with meat and cheese; very satiating."),
        "lobster_bisque" to FoodDishInfo("lobster_bisque", "Lobster Bisque", 250.0, 130.0, 4.5, 8.0, 9.0, "Soup", 68, "Creamy seafood soup."),
        "lobster_roll_sandwich" to FoodDishInfo("lobster_roll_sandwich", "Lobster Roll", 200.0, 210.0, 16.0, 18.0, 9.0, "Main Dish", 75, "Sweet lobster meat on a toasted roll."),
        "macaroni_and_cheese" to FoodDishInfo("macaroni_and_cheese", "Mac and Cheese", 250.0, 210.0, 8.0, 24.0, 9.5, "Main Dish", 62, "Comfort food high in energy."),
        "macarons" to FoodDishInfo("macarons", "Macarons", 60.0, 410.0, 7.0, 58.0, 18.0, "Dessert", 36, "Almond flour cookies with ganache filling."),
        "miso_soup" to FoodDishInfo("miso_soup", "Miso Soup", 200.0, 38.0, 3.0, 4.0, 1.2, "Soup", 88, "Fermented soybean soup supporting gut health."),
        "mussels" to FoodDishInfo("mussels", "Steamed Mussels", 200.0, 105.0, 14.0, 4.0, 3.0, "Main Dish", 92, "Exceptional zinc, iron, and vitamin B12."),
        "nachos" to FoodDishInfo("nachos", "Nachos", 200.0, 320.0, 8.0, 34.0, 18.0, "Appetizer", 50, "Tortilla chips with melted cheese and toppings."),
        "omelette" to FoodDishInfo("omelette", "Omelette", 180.0, 154.0, 11.0, 1.0, 12.0, "Main Dish", 85, "Classic complete protein with near-zero carbs."),
        "onion_rings" to FoodDishInfo("onion_rings", "Onion Rings", 120.0, 330.0, 3.8, 38.0, 18.0, "Side Dish", 42, "Batter fried onions; high in oil."),
        "oysters" to FoodDishInfo("oysters", "Oysters", 150.0, 68.0, 7.0, 4.0, 2.5, "Appetizer", 94, "Top dietary source of bioavailable zinc."),
        "pad_thai" to FoodDishInfo("pad_thai", "Pad Thai", 300.0, 165.0, 7.5, 23.0, 5.5, "Main Dish", 74, "Stir-fried rice noodles with eggs and peanuts."),
        "paella" to FoodDishInfo("paella", "Paella", 300.0, 155.0, 9.0, 21.0, 4.0, "Main Dish", 82, "Saffron rice with a variety of seafood and poultry."),
        "pancakes" to FoodDishInfo("pancakes", "Pancakes", 150.0, 227.0, 6.0, 38.0, 6.0, "Breakfast", 60, "Fluffy griddle cakes; best enjoyed with fruit."),
        "panna_cotta" to FoodDishInfo("panna_cotta", "Panna Cotta", 120.0, 250.0, 3.0, 22.0, 17.0, "Dessert", 42, "Italian cream dessert."),
        "peking_duck" to FoodDishInfo("peking_duck", "Peking Duck", 200.0, 337.0, 19.0, 0.0, 28.0, "Main Dish", 65, "Crispy skin roasted duck, high in flavor and fat."),
        "pho" to FoodDishInfo("pho", "Pho Noodle Soup", 400.0, 95.0, 6.5, 13.0, 1.8, "Main Dish", 88, "Aromatic bone broth, herbs, rice noodles and beef."),
        "pizza" to FoodDishInfo("pizza", "Pizza", 180.0, 266.0, 11.0, 33.0, 10.0, "Main Dish", 66, "Balanced carb and protein baseline."),
        "pork_chop" to FoodDishInfo("pork_chop", "Pork Chop", 200.0, 210.0, 25.0, 0.0, 12.0, "Main Dish", 82, "Lean cut of pork provides thiamine and protein."),
        "poutine" to FoodDishInfo("poutine", "Poutine", 250.0, 260.0, 7.0, 28.0, 14.0, "Main Dish", 46, "Fries with cheese curds and rich gravy."),
        "prime_rib" to FoodDishInfo("prime_rib", "Prime Rib", 220.0, 320.0, 21.0, 0.0, 26.0, "Main Dish", 62, "Hearty beef roast; high saturated fat."),
        "pulled_pork_sandwich" to FoodDishInfo("pulled_pork_sandwich", "Pulled Pork Sandwich", 220.0, 230.0, 15.0, 22.0, 9.5, "Main Dish", 68, "Slow cooked seasoned pork."),
        "ramen" to FoodDishInfo("ramen", "Ramen", 400.0, 110.0, 5.5, 14.0, 3.8, "Main Dish", 75, "Comforting Japanese noodle bowl."),
        "ravioli" to FoodDishInfo("ravioli", "Ravioli", 220.0, 185.0, 8.0, 28.0, 4.5, "Main Dish", 76, "Filled pasta pockets with ricotta or meat."),
        "red_velvet_cake" to FoodDishInfo("red_velvet_cake", "Red Velvet Cake", 120.0, 365.0, 4.0, 50.0, 17.0, "Dessert", 32, "Sweet cake with cream cheese icing."),
        "risotto" to FoodDishInfo("risotto", "Risotto", 250.0, 160.0, 4.5, 24.0, 5.0, "Main Dish", 74, "Creamy Arborio rice prepared with broth."),
        "samosa" to FoodDishInfo("samosa", "Samosa", 100.0, 260.0, 4.5, 30.0, 14.0, "Appetizer", 55, "Crispy spiced pastry filled with potatoes/peas."),
        "sashimi" to FoodDishInfo("sashimi", "Sashimi", 150.0, 130.0, 24.0, 0.0, 3.5, "Main Dish", 98, "Pure raw fish; pristine protein and omega-3s."),
        "scallops" to FoodDishInfo("scallops", "Seared Scallops", 150.0, 110.0, 20.0, 5.0, 1.0, "Main Dish", 95, "Extremely lean seafood packed with protein."),
        "seaweed_salad" to FoodDishInfo("seaweed_salad", "Seaweed Salad", 120.0, 70.0, 2.0, 10.0, 2.5, "Salad", 92, "Rich in iodine, trace minerals and dietary fiber."),
        "shrimp_and_grits" to FoodDishInfo("shrimp_and_grits", "Shrimp and Grits", 250.0, 170.0, 11.0, 17.0, 6.5, "Main Dish", 77, "Southern classic featuring lean shrimp."),
        "spaghetti_bolognese" to FoodDishInfo("spaghetti_bolognese", "Spaghetti Bolognese", 300.0, 145.0, 7.5, 20.0, 4.0, "Main Dish", 78, "Pasta with slow-cooked meat and tomato ragu."),
        "spaghetti_carbonara" to FoodDishInfo("spaghetti_carbonara", "Spaghetti Carbonara", 280.0, 220.0, 10.0, 23.0, 9.5, "Main Dish", 68, "Rich Roman pasta with eggs, pecorino and guanciale."),
        "spring_rolls" to FoodDishInfo("spring_rolls", "Spring Rolls", 120.0, 190.0, 5.0, 24.0, 8.5, "Appetizer", 70, "Crispy vegetable or meat wrapped rolls."),
        "steak" to FoodDishInfo("steak", "Steak", 220.0, 220.0, 27.0, 0.0, 12.0, "Main Dish", 84, "High bioavailability iron, zinc, and protein."),
        "strawberry_shortcake" to FoodDishInfo("strawberry_shortcake", "Strawberry Shortcake", 150.0, 260.0, 3.5, 36.0, 12.0, "Dessert", 48, "Fresh strawberries with sweet cream and biscuit."),
        "sushi" to FoodDishInfo("sushi", "Sushi Rolls", 220.0, 145.0, 6.5, 26.0, 1.8, "Main Dish", 88, "Nori seaweed, fish, and seasoned rice."),
        "tacos" to FoodDishInfo("tacos", "Tacos", 180.0, 210.0, 12.0, 20.0, 9.0, "Main Dish", 76, "Corn tortillas packed with seasoned meat and salsa."),
        "takoyaki" to FoodDishInfo("takoyaki", "Takoyaki", 150.0, 170.0, 7.0, 22.0, 6.0, "Appetizer", 70, "Japanese octopus balls with savory sauce."),
        "tiramisu" to FoodDishInfo("tiramisu", "Tiramisu", 130.0, 285.0, 4.5, 32.0, 16.0, "Dessert", 42, "Espresso dipped ladyfingers with mascarpone."),
        "tuna_tartare" to FoodDishInfo("tuna_tartare", "Tuna Tartare", 150.0, 130.0, 24.0, 1.0, 3.0, "Appetizer", 96, "High quality lean tuna with healthy seasonings."),
        "waffles" to FoodDishInfo("waffles", "Waffles", 120.0, 290.0, 6.0, 41.0, 11.5, "Breakfast", 54, "Crispy griddle cakes; top with fresh berries.")
    )

    fun getDish(id: String): FoodDishInfo? {
        val clean = id.trim().lowercase().replace(' ', '_')
        return dishes[clean]
    }

    fun allDishes(): List<FoodDishInfo> = dishes.values.toList()
}
