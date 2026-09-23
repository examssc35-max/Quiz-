package com.example.data.samples

import com.example.data.model.QuestionSchema
import com.example.data.model.QuizJsonParser
import com.example.data.model.QuizSchema

object SampleQuizzes {

    val BENGALI_QUIZ = QuizSchema(
        version = 1,
        title = "বাংলাদেশ ও সাধারণ জ্ঞান",
        description = "বাংলাদেশের ইতিহাস, ঐতিহ্য, ভূগোল ও বিজ্ঞানের মৌলিক ধারণাসমূহ নিয়ে বিশেষ কুইজ।",
        category = "বাংলাদেশ",
        difficulty = "Medium",
        timeLimit = 300,
        shuffleQuestions = true,
        shuffleOptions = true,
        questions = listOf(
            QuestionSchema(
                id = "bn_q1",
                question = "বাংলাদেশের জাতীয় কবি কে?",
                options = listOf(
                    "কাজী নজরুল ইসলাম",
                    "রবীন্দ্রনাথ ঠাকুর",
                    "জসীম উদ্‌দীন",
                    "জীবনানন্দ দাশ"
                ),
                answer = 0,
                points = 1,
                explanation = "কাজী নজরুল ইসলাম বাংলাদেশের জাতীয় কবি। তিনি বিদ্রোহী কবি নামেও সমধিক পরিচিত।"
            ),
            QuestionSchema(
                id = "bn_q2",
                question = "বাংলাদেশের সংবিধান কত সালে কার্যকর হয়?",
                options = listOf(
                    "১৯৭১ সালে",
                    "১৯৭২ সালে",
                    "১৯৭৩ সালে",
                    "১৯৭৫ সালে"
                ),
                answer = 1,
                points = 1,
                explanation = "১৬ ডিসেম্বর ১৯৭২ সালে স্বাধীন বাংলাদেশের সংবিধান কার্যকর হয়।"
            ),
            QuestionSchema(
                id = "bn_q3",
                question = "পদ্মা ও যমুনা নদী কোথায় মিলিত হয়েছে?",
                options = listOf(
                    "চাঁদপুরে",
                    "গোয়ালন্দে",
                    "ভৈরবে",
                    "আরিচায়"
                ),
                answer = 1,
                points = 1,
                explanation = "পদ্মা ও যমুনা নদী রাজবাড়ী জেলার গোয়ালন্দে মিলিত হয়েছে।"
            ),
            QuestionSchema(
                id = "bn_q4",
                question = "পানির রাসায়নিক সংকেত কোনটি?",
                options = listOf(
                    "CO2",
                    "H2O",
                    "NaCl",
                    "O2"
                ),
                answer = 1,
                points = 1,
                explanation = "পানির রাসায়নিক সংকেত H2O, যা দুটি হাইড্রোজেন ও একটি অক্সিজেন পরমাণু দ্বারা গঠিত।"
            ),
            QuestionSchema(
                id = "bn_q5",
                question = "উদ্ভিদ সালোকসংশ্লেষণের জন্য কোন গ্যাস গ্রহণ করে?",
                options = listOf(
                    "অক্সিজেন",
                    "নাইট্রোজেন",
                    "কার্বন ডাই-অক্সাইড",
                    "হাইড্রোজেন"
                ),
                answer = 2,
                points = 1,
                explanation = "উদ্ভিদ সূর্যালোকের উপস্থিতিতে বাতাস থেকে কার্বন ডাই-অক্সাইড (CO2) গ্রহণ করে খাদ্য তৈরি করে।"
            ),
            QuestionSchema(
                id = "bn_q6",
                question = "সূর্যজগতে লাল গ্রহ (Red Planet) হিসেবে পরিচিত কোনটি?",
                options = listOf(
                    "শুক্র",
                    "মঙ্গল",
                    "বৃহস্পতি",
                    "বুধ"
                ),
                answer = 1,
                points = 1,
                explanation = "মঙ্গল গ্রহের পৃষ্ঠে প্রচুর আয়রন অক্সাইড থাকায় একে লাল গ্রহ বলা হয়।"
            ),
            QuestionSchema(
                id = "bn_q7",
                question = "কম্পিউটারের মস্তিষ্ক (Brain of Computer) কাকে বলা হয়?",
                options = listOf(
                    "RAM",
                    "CPU",
                    "Hard Disk",
                    "Monitor"
                ),
                answer = 1,
                points = 1,
                explanation = "CPU (Central Processing Unit) কম্পিউটারের সকল নির্দেশ ও হিসাব প্রক্রিয়াজাত করে।"
            ),
            QuestionSchema(
                id = "bn_q8",
                question = "সুন্দরবন কয়টি দেশের মধ্যে বিস্তৃত?",
                options = listOf(
                    "১টি",
                    "২টি",
                    "৩টি",
                    "৪টি"
                ),
                answer = 1,
                points = 1,
                explanation = "সুন্দরবন বাংলাদেশ ও ভারত - এই ২টি দেশের মধ্যে বিস্তৃত (প্রায় ৬০% বাংলাদেশে এবং ৪০% ভারতে)।"
            ),
            QuestionSchema(
                id = "bn_q9",
                question = "৭ + ৮ × ২ এর মান কত?",
                options = listOf(
                    "৩০",
                    "২৩",
                    "২২",
                    "১৫"
                ),
                answer = 1,
                points = 1,
                explanation = "বডমাস (BODMAS) নিয়ম অনুসারে গুণের কাজ আগে হবে: ৮ × ২ = ১৬, এরপর ৭ + ১৬ = ২৩।"
            ),
            QuestionSchema(
                id = "bn_q10",
                question = "আন্তর্জাতিক মাতৃভাষা দিবস কোন তারিখে উদযাপিত হয়?",
                options = listOf(
                    "২৬ মার্চ",
                    "১৬ ডিসেম্বর",
                    "২১ ফেব্রুয়ারি",
                    "১ মে"
                ),
                answer = 2,
                points = 1,
                explanation = "১৯৯৯ সালে ইউনেস্কো ২১ ফেব্রুয়ারিকে আন্তর্জাতিক মাতৃভাষা দিবস হিসেবে স্বীকৃতি দেয়।"
            )
        )
    )

    val GENERAL_KNOWLEDGE_QUIZ = QuizSchema(
        version = 1,
        title = "General Knowledge Master",
        description = "Test your awareness of world geography, history, and iconic landmarks.",
        category = "General Knowledge",
        difficulty = "Easy",
        timeLimit = 240,
        shuffleQuestions = true,
        shuffleOptions = true,
        questions = listOf(
            QuestionSchema(
                id = "gk_q1",
                question = "Which planet is known as the Red Planet?",
                options = listOf("Earth", "Mars", "Jupiter", "Venus"),
                answer = 1,
                points = 1,
                explanation = "Mars is called the Red Planet because of iron oxide (rust) covering its surface."
            ),
            QuestionSchema(
                id = "gk_q2",
                question = "What is the capital of France?",
                options = listOf("Berlin", "Madrid", "Paris", "Rome"),
                answer = 2,
                points = 1,
                explanation = "Paris is the capital and most populous city of France."
            ),
            QuestionSchema(
                id = "gk_q3",
                question = "Which is the largest ocean on Earth?",
                options = listOf("Atlantic Ocean", "Indian Ocean", "Arctic Ocean", "Pacific Ocean"),
                answer = 3,
                points = 1,
                explanation = "The Pacific Ocean covers more than 30% of the Earth's surface."
            ),
            QuestionSchema(
                id = "gk_q4",
                question = "In which country can you visit the ancient Colosseum?",
                options = listOf("Greece", "Italy", "Egypt", "Spain"),
                answer = 1,
                points = 1,
                explanation = "The Colosseum is an oval amphitheatre situated in the centre of Rome, Italy."
            ),
            QuestionSchema(
                id = "gk_q5",
                question = "How many continents are there on Earth?",
                options = listOf("5", "6", "7", "8"),
                answer = 2,
                points = 1,
                explanation = "Earth has 7 continents: Asia, Africa, North America, South America, Antarctica, Europe, and Australia."
            )
        )
    )

    val SCIENCE_QUIZ = QuizSchema(
        version = 1,
        title = "Science & Wonders of Universe",
        description = "Explore chemistry, physics, and cosmic wonders with this science challenge.",
        category = "Science",
        difficulty = "Medium",
        timeLimit = 300,
        shuffleQuestions = true,
        shuffleOptions = true,
        questions = listOf(
            QuestionSchema(
                id = "sc_q1",
                question = "What is the chemical formula for water?",
                options = listOf("CO2", "H2O", "CH4", "NaCl"),
                answer = 1,
                points = 1,
                explanation = "Water consists of two hydrogen atoms bonded to one oxygen atom (H2O)."
            ),
            QuestionSchema(
                id = "sc_q2",
                question = "What gas do plants primarily absorb during photosynthesis?",
                options = listOf("Oxygen", "Nitrogen", "Carbon Dioxide", "Hydrogen"),
                answer = 2,
                points = 1,
                explanation = "Plants take in Carbon Dioxide and release Oxygen during photosynthesis."
            ),
            QuestionSchema(
                id = "sc_q3",
                question = "What is the closest star to Earth?",
                options = listOf("Proxima Centauri", "Sirius", "The Sun", "Betelgeuse"),
                answer = 2,
                points = 1,
                explanation = "The Sun is the star at the center of the Solar System, about 93 million miles from Earth."
            ),
            QuestionSchema(
                id = "sc_q4",
                question = "What is the hardest known natural mineral on Earth?",
                options = listOf("Gold", "Iron", "Diamond", "Quartz"),
                answer = 2,
                points = 1,
                explanation = "Diamond ranks 10 on the Mohs hardness scale, making it the hardest natural mineral."
            ),
            QuestionSchema(
                id = "sc_q5",
                question = "At what temperature Celsius does pure water freeze?",
                options = listOf("0°C", "32°C", "-10°C", "100°C"),
                answer = 0,
                points = 1,
                explanation = "Under standard atmospheric pressure, water freezes at 0 degrees Celsius."
            )
        )
    )

    val MATH_QUIZ = QuizSchema(
        version = 1,
        title = "Math & Logic Practice",
        description = "Sharpen your arithmetic, pattern recognition, and mental math speed.",
        category = "Mathematics",
        difficulty = "Hard",
        timeLimit = 180,
        shuffleQuestions = true,
        shuffleOptions = true,
        questions = listOf(
            QuestionSchema(
                id = "ma_q1",
                question = "What is 15 × 12?",
                options = listOf("160", "175", "180", "190"),
                answer = 2,
                points = 1,
                explanation = "15 × 10 = 150, 15 × 2 = 30, so 150 + 30 = 180."
            ),
            QuestionSchema(
                id = "ma_q2",
                question = "What is the square root of 144?",
                options = listOf("11", "12", "13", "14"),
                answer = 1,
                points = 1,
                explanation = "12 × 12 = 144, hence √144 = 12."
            ),
            QuestionSchema(
                id = "ma_q3",
                question = "Which number is the only even prime number?",
                options = listOf("0", "1", "2", "4"),
                answer = 2,
                points = 1,
                explanation = "2 is the smallest and only even prime number."
            ),
            QuestionSchema(
                id = "ma_q4",
                question = "If 3x + 5 = 20, what is the value of x?",
                options = listOf("3", "5", "6", "7"),
                answer = 1,
                points = 1,
                explanation = "3x = 20 - 5 = 15 => x = 15 / 3 = 5."
            ),
            QuestionSchema(
                id = "ma_q5",
                question = "What comes next in the sequence: 2, 4, 8, 16, ...?",
                options = listOf("24", "30", "32", "64"),
                answer = 2,
                points = 1,
                explanation = "Each term is multiplied by 2 (powers of 2), so 16 × 2 = 32."
            )
        )
    )

    val ICT_QUIZ = QuizSchema(
        version = 1,
        title = "ICT & Computer Fundamentals",
        description = "Essential concepts of modern computing, hardware, software, and the web.",
        category = "ICT",
        difficulty = "Easy",
        timeLimit = 240,
        shuffleQuestions = true,
        shuffleOptions = true,
        questions = listOf(
            QuestionSchema(
                id = "ict_q1",
                question = "What does HTTP stand for?",
                options = listOf(
                    "HyperText Transfer Protocol",
                    "HyperTech Translation Program",
                    "High Traffic Test Process",
                    "Host Terminal Transport Path"
                ),
                answer = 0,
                points = 1,
                explanation = "HTTP stands for HyperText Transfer Protocol, the foundation of data communication for the World Wide Web."
            ),
            QuestionSchema(
                id = "ict_q2",
                question = "Which computer component is known as volatile memory?",
                options = listOf("Hard Disk", "ROM", "RAM", "SSD"),
                answer = 2,
                points = 1,
                explanation = "RAM (Random Access Memory) loses all its stored data when the power is turned off."
            ),
            QuestionSchema(
                id = "ict_q3",
                question = "How many bits are in one single Byte?",
                options = listOf("4", "8", "16", "32"),
                answer = 1,
                points = 1,
                explanation = "1 Byte is composed of 8 binary digits (bits)."
            ),
            QuestionSchema(
                id = "ict_q4",
                question = "Which file extension is commonly used for JSON data?",
                options = listOf(".js", ".json", ".txt", ".xml"),
                answer = 1,
                points = 1,
                explanation = ".json is the standard file extension for JavaScript Object Notation data."
            )
        )
    )

    val ALL_SAMPLES = listOf(
        BENGALI_QUIZ,
        GENERAL_KNOWLEDGE_QUIZ,
        SCIENCE_QUIZ,
        MATH_QUIZ,
        ICT_QUIZ
    )

    val SAMPLE_JSON_PREVIEW = QuizJsonParser.toJsonString(BENGALI_QUIZ, 2)
}
