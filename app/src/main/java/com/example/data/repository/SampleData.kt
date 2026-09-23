package com.example.data.repository

import com.example.data.model.ArticleEntity
import com.example.data.model.CourseEntity
import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity

object SampleData {

    val sampleCourses: List<CourseEntity> = listOf(
        CourseEntity(
            id = "course_gre_essential",
            title = "GRE Essential 350",
            description = "High-frequency advanced vocabulary for GRE and academic reading",
            createdAt = 1700000000000L
        ),
        CourseEntity(
            id = "course_ielts_academic",
            title = "IELTS Academic Core",
            description = "Core academic words for IELTS preparation, writing & speaking",
            createdAt = 1700000010000L
        ),
        CourseEntity(
            id = "course_bilingual_daily",
            title = "দৈনন্দিন বাংলা ও ইংরেজি শব্দসম্ভার",
            description = "প্রয়োজনীয় ইংরেজি শব্দ ও তাদের প্রাঞ্জল বাংলা অর্থ",
            createdAt = 1700000020000L
        )
    )

    val sampleWords: List<VocabularyWordEntity> = listOf(
        // Course 1: GRE Essential 350
        VocabularyWordEntity(
            id = "word_gre_1",
            word = "Abate",
            meaning = "তীব্রতা হ্রাস পাওয়া, কমে যাওয়া; To lessen in intensity or degree; subside",
            group = "1",
            synonyms = "Subside, diminish, lessen, decline, curtail",
            extraWord = "Abatement (noun): The abatement of the storm brought relief.",
            example = "The violent storm began to abate as dawn broke across the valley.",
            mnemonic = "Think of 'a-bate': eating food will abate hunger.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_2",
            word = "Aberrant",
            meaning = "অস্বাভাবিক, নিয়মবহির্ভূত; Deviating from the normal, usual, or expected",
            group = "1",
            synonyms = "Abnormal, anomalous, deviant, divergent, erratic",
            extraWord = "Aberration (noun): The violent surge was an aberration in the quiet town.",
            example = "His aberrant behavior at the symposium drew perplexed glances from peers.",
            mnemonic = "Aberrant sounds like 'a bear errant' wandering away from normal paths.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_3",
            word = "Cacophony",
            meaning = "কর্কশ শব্দের সংমিশ্রণ, বিকট আওয়াজ; A harsh, discordant mixture of sounds",
            group = "1",
            synonyms = "Dissonance, clamor, racket, discord, din",
            extraWord = "Cacophonous (adj): A cacophonous sound of construction drills outside.",
            example = "The bustling street market was filled with a cacophony of vendors and horns.",
            mnemonic = "Caco (bad) + phone (sound) = unpleasant jarring noise.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_4",
            word = "Ephemeral",
            meaning = "ক্ষণস্থায়ী, ক্ষণভঙ্গুর; Lasting for a very short time; fleeting",
            group = "1",
            synonyms = "Fleeting, transient, evanescent, short-lived, momentary",
            extraWord = "Ephemera (noun): Collecting historical ephemera like concert tickets.",
            example = "The golden sunset was gorgeous but ephemeral, disappearing in minutes.",
            mnemonic = "Sounds like 'funeral' - life's moments are short and fleeting.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_5",
            word = "Garrulous",
            meaning = "বাচাল, অতিরিক্ত কথা বলা; Excessively talkative, especially on trivial matters",
            group = "1",
            synonyms = "Loquacious, talkative, voluble, chatty, verbose",
            extraWord = "Garrulity (noun): His endless garrulity tested everyone's patience.",
            example = "The garrulous driver narrated his entire life story during the brief trip.",
            mnemonic = "Chatty in a garage -> garrulous.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_6",
            word = "Meticulous",
            meaning = "খুঁতখুঁতে, অত্যন্ত সতর্ক; Showing great attention to detail; very careful and precise",
            group = "1",
            synonyms = "Scrupulous, punctilious, painstaking, fastidious, thorough",
            extraWord = "Meticulousness (noun): Meticulousness is required when analyzing data.",
            example = "She was meticulous about keeping her lab notebooks error-free.",
            mnemonic = "Meticulous = 'Medal for every tiny detail'.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_7",
            word = "Ostentatious",
            meaning = "লোকদেখানো, আড়ম্বরপূর্ণ; Characterized by pretentious or showy display to impress",
            group = "2",
            synonyms = "Pretentious, flamboyant, showy, gaudy, conspicuous",
            extraWord = "Ostentation (noun): Displaying gold watches purely for ostentation.",
            example = "The tycoon arrived in an ostentatious gilded carriage to make a scene.",
            mnemonic = "O-STUNT-tatious: doing stunts to show off.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_8",
            word = "Pragmatic",
            meaning = "বাস্তবধর্মী, বাস্তবসম্মত; Dealing with things sensibly and realistically",
            group = "2",
            synonyms = "Practical, realistic, sensible, rational, utilitarian",
            extraWord = "Pragmatism (noun): He approached corporate governance with pragmatism.",
            example = "Instead of wishful thinking, she devised a pragmatic strategy for cost cutting.",
            mnemonic = "Pragmatic sounds like Practical Mathematics.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_9",
            word = "Reticent",
            meaning = "স্বল্পভাষী, চাপা স্বভাবের; Not revealing one's thoughts or feelings readily; reserved",
            group = "2",
            synonyms = "Reserved, taciturn, quiet, introverted, guarded",
            extraWord = "Reticence (noun): Her reticence was often mistaken for haughtiness.",
            example = "He remained reticent about his private life despite probing questions.",
            mnemonic = "Silent on request: re-ticket.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_10",
            word = "Ubiquitous",
            meaning = "সর্বব্যাপী, সর্বত্র বিরাজমান; Present, appearing, or found everywhere; omnipresent",
            group = "2",
            synonyms = "Omnipresent, pervasive, everywhere, universal, rampant",
            extraWord = "Ubiquity (noun): The ubiquity of smartphones changed modern social dynamics.",
            example = "Coffee shops have become ubiquitous in almost every major downtown.",
            mnemonic = "Mosquitoes 'bite us' everywhere: U-biquitous.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_11",
            word = "Venerate",
            meaning = "গভীর শ্রদ্ধা করা, পূজা করা; To regard with great respect; revere or honor",
            group = "3",
            synonyms = "Revere, worship, respect, idolize, honor",
            extraWord = "Venerable (adj): The venerable professor received a standing ovation.",
            example = "Many ancient cultures venerate nature as sacred and divine.",
            mnemonic = "Ven-rate: when you rate someone highest with great respect.",
            status = "unrated",
            courseId = "course_gre_essential"
        ),
        VocabularyWordEntity(
            id = "word_gre_12",
            word = "Zealous",
            meaning = "উদ্দীপ্ত, অতি উৎসাহী; Having or showing great energy, enthusiasm, or fervor",
            group = "3",
            synonyms = "Fervent, passionate, ardent, avid, dedicated",
            extraWord = "Zealot (noun): He was an uncompromising zealot for constitutional reform.",
            example = "The zealous volunteers planted thousands of trees across the hillside.",
            mnemonic = "Full of ZEAL (enthusiasm).",
            status = "unrated",
            courseId = "course_gre_essential"
        ),

        // Course 2: IELTS Academic Core
        VocabularyWordEntity(
            id = "word_ielts_1",
            word = "Diminish",
            meaning = "হ্রাস পাওয়া, কমিয়ে দেওয়া; Make or become less; decrease",
            group = "1",
            synonyms = "Decline, decrease, abate, reduce, lessen",
            extraWord = "Diminution (noun): A diminution of company profits during the crisis.",
            example = "The patient's chronic pain will diminish with proper medication and rest.",
            mnemonic = "Diminish sounds like 'mini' - making things smaller.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_2",
            word = "Eloquent",
            meaning = "বাকপটু, আকর্ষণীয় বক্তা; Fluent or persuasive in speaking or writing",
            group = "1",
            synonyms = "Articulate, expressive, fluent, persuasive, silver-tongued",
            extraWord = "Eloquence (noun): Her eloquence moved the entire international assembly.",
            example = "The delegate delivered an eloquent speech defending global environmental treaties.",
            mnemonic = "Eloquent = elegant + vocal eloquence.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_3",
            word = "Diligent",
            meaning = "অধ্যবসায়ী, পরিশ্রমী; Having or showing care and conscientiousness in one's work",
            group = "1",
            synonyms = "Hardworking, assiduous, persistent, meticulous, industrious",
            extraWord = "Diligence (noun): Diligence is key to academic distinction.",
            example = "Diligent researchers double-check every calculation before publishing.",
            mnemonic = "Diligent people do work with diligence.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_4",
            word = "Subside",
            meaning = "কমে যাওয়া, শান্ত হওয়া; Become less intense, violent, or severe",
            group = "1",
            synonyms = "Abate, recede, settle, lessen, ease",
            extraWord = "Subsidence (noun): The subsidence of flood waters revealed the roads.",
            example = "After heavy rains for forty-eight hours, the river waters finally began to subside.",
            mnemonic = "Sub-side: tensions sit by the side and calm down.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_5",
            word = "Resilient",
            meaning = "সহনশীল, ঘুরে দাঁড়াতে সক্ষম; Able to withstand or recover quickly from difficult conditions",
            group = "2",
            synonyms = "Tough, adaptable, strong, hardy, buoyant",
            extraWord = "Resilience (noun): The community showed remarkable resilience after the storm.",
            example = "The resilient local economy rebounded swiftly after the natural disaster.",
            mnemonic = "Re-silent -> bounces back repeatedly without breaking.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_6",
            word = "Pragmatic",
            meaning = "বাস্তবধর্মী, বাস্তবোপযোগী; Dealing with problems in a sensible and practical way",
            group = "2",
            synonyms = "Practical, sensible, realistic, functional",
            extraWord = "Pragmatism (noun): Pragmatism helped them achieve consensus.",
            example = "Governments must adopt pragmatic environmental policies that industry can sustain.",
            mnemonic = "Pragmatic = practical attitude.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_7",
            word = "Perceptive",
            meaning = "তীক্ষ্ণদৃষ্টিসম্পন্ন, সজাগ; Having or showing sensitive insight and keen observation",
            group = "2",
            synonyms = "Insightful, discerning, observant, intuitive, sharp",
            extraWord = "Perception (noun): Visual perception allows us to analyze art.",
            example = "Her perceptive analysis revealed subtle trends that others had overlooked.",
            mnemonic = "Perceive + active = perceptive observation.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),
        VocabularyWordEntity(
            id = "word_ielts_8",
            word = "Innovative",
            meaning = "উদ্ভাবনী, আধুনিক; Featuring new methods, advanced ideas, or original thinking",
            group = "2",
            synonyms = "Creative, original, novel, pioneering, cutting-edge",
            extraWord = "Innovation (noun): Technological innovation drives economic growth.",
            example = "The laboratory is celebrated for developing innovative renewable energy solutions.",
            mnemonic = "In-nova: new star / fresh ideas.",
            status = "unrated",
            courseId = "course_ielts_academic"
        ),

        // Course 3: দৈনন্দিন বাংলা ও ইংরেজি শব্দসম্ভার
        VocabularyWordEntity(
            id = "word_bilingual_1",
            word = "Perseverance",
            meaning = "অধ্যবসায়, একনিষ্ঠ চেষ্টা; Persistence in doing something despite difficulty or delay",
            group = "1",
            synonyms = "Tenacity, persistence, determination, stamina",
            extraWord = "Persevere (verb): She persevered through all hardships.",
            example = "Through hard work and perseverance, she overcame every obstacle to graduate at the top.",
            mnemonic = "Per-sever-ance: severe conditions won't stop you.",
            status = "unrated",
            courseId = "course_bilingual_daily"
        ),
        VocabularyWordEntity(
            id = "word_bilingual_2",
            word = "Compassion",
            meaning = "সহমর্মিতা, করুণা, পরোপকার; Sympathetic pity and concern for the sufferings of others",
            group = "1",
            synonyms = "Empathy, kindness, sympathy, benevolence",
            extraWord = "Compassionate (adj): A compassionate medical team.",
            example = "True leadership is demonstrated by compassion and understanding toward vulnerable citizens.",
            mnemonic = "Com (together) + passion (feeling) = feeling together.",
            status = "unrated",
            courseId = "course_bilingual_daily"
        ),
        VocabularyWordEntity(
            id = "word_bilingual_3",
            word = "Serenity",
            meaning = "প্রশান্তি, নির্মল চিত্ত; The state of being calm, peaceful, and untroubled",
            group = "1",
            synonyms = "Peace, tranquility, calm, stillness, placidity",
            extraWord = "Serene (adj): The serene mountain landscape at sunrise.",
            example = "Early morning walks through the quiet garden brought deep serenity and mental clarity.",
            mnemonic = "Serene = tranquil peace.",
            status = "unrated",
            courseId = "course_bilingual_daily"
        ),
        VocabularyWordEntity(
            id = "word_bilingual_4",
            word = "Abate",
            meaning = "উপশম হওয়া, কমে আসা; To become smaller or less intense",
            group = "1",
            synonyms = "Lessen, ease, diminish, subside",
            extraWord = "Abatement: Noticeable abatement of symptoms.",
            example = "The doctor assured the patient that the high fever would abate soon.",
            mnemonic = "Eating food will abate hunger.",
            status = "unrated",
            courseId = "course_bilingual_daily"
        )
    )

    val sampleArticles: List<ArticleEntity> = listOf(
        ArticleEntity(
            id = "art_sample_1",
            title = "The Architecture of Daily Focus: Cultivating Mental Clarity",
            content = """In our modern era, the relentless pace of daily life can easily overwhelm the human mind. From dawn until dusk, we find ourselves surrounded by a relentless cacophony of digital notifications, urgent messages, and competing obligations. Many people mistakenly assume that grand, ostentatious gestures are necessary to transform one's destiny. In reality, enduring mental clarity is forged through quiet, pragmatic decisions practiced consistently day after day.

When chaos and panic threaten to derail your equilibrium, the wisest first step is simply to pause and wait for the inner turbulence to abate. Just as violent physical storms inevitably spend their energy, mental anxiety will subside if given patience and breathing space. Instead of reacting with frantic haste, the diligent thinker adopts a meticulous routine: focusing on one essential project at a time, decluttering the workspace, and cultivating periods of deliberate silence.

We must also recognize that superficial praise and transient accolades are strictly ephemeral. Wasting precious hours trying to please an insatiably garrulous crowd drains our creative reservoir. Those who truly master their craft remain quiet and reticent about their daily struggles; they develop resilient discipline and learn to venerate the sacred solitude of deep work.

When daily actions are anchored to authentic values, high focus is no longer an aberrant surprise—it becomes a ubiquitous and harmonious way of being. With zealous devotion to continuous learning, every setback transforms into an enduring foundation for mastery.""".trimIndent(),
            author = "Dr. Marcus Vance",
            courseId = "course_gre_essential",
            createdAt = 1700000000000L,
            wordCount = 230
        ),
        ArticleEntity(
            id = "art_sample_2",
            title = "শব্দ ও চেতনার দিগন্ত: ভাষার শক্তি এবং বোধের বিকাশ",
            content = """মানুষের চিন্তা ও অনুভূতির সবচেয়ে শক্তিশালী বাহন হলো ভাষা। একটি সমৃদ্ধ শব্দভাণ্ডার কেবল নতুন বাক্য তৈরি করতে শেখায় না, বরং চিন্তার গভীরতা ও সূক্ষ্মতা বাড়িয়ে তোলে। মনের ভাব যখন আমরা সুনির্দিষ্ট ভাষায় প্রকাশ করতে পারি, তখন বক্তব্যটি অত্যন্ত eloquent এবং আকর্ষণীয় হয়ে ওঠে।

জীবনের পথচলায় যখন নানাবিধ প্রতিকূলতা ও মানসিক অস্থিরতা দেখা দেয়, তখন ভেতরের সংশয় abate হওয়া বা প্রশমিত হওয়ার জন্য প্রয়োজন গভীর ধৈর্য ও serenity। সমাজে অনেকেই নিজেদের ক্ষণিকের অর্জনে ostentatious বা লোকদেখানো প্রদর্শন করতে ভালোবাসে, কিন্তু প্রকৃত জ্ঞানী মানুষেরা সর্বদা reticent বা স্বল্পভাষী থাকেন। তারা হৃদয়ঙ্গম করেন যে বাহ্যিক কোলাহল একান্তই ephemeral বা ক্ষণস্থায়ী, পক্ষান্তরে অভ্যন্তরীণ আত্মবিশ্বাস ও প্রজ্ঞা চিরস্থায়ী।

যেকোনো মহৎ লক্ষ্য অর্জনের মূলে রয়েছে diligent সাধনা এবং বাস্তবসম্মত বা pragmatic পরিকল্পনা। সমাজ যখন পারস্পরিক বিরোধ ও কর্কশ cacophony দিয়ে ঢেকে যায়, তখন কেবল সহমর্মিতা বা compassion এবং নিষ্ঠাবান অধ্যবসায় (perseverance) আমাদের সঠিক আলোকবর্তিকা দেখাতে পারে। তাই প্রতিদিন নতুন নতুন শব্দ আয়ত্ত করা এবং মননশীল পাঠ্যাভ্যাস গড়ে তোলা মানুষের চেতনা ও মননশীলতাকে এক অনন্য উচ্চতায় নিয়ে যায়।""".trimIndent(),
            author = "অধ্যাপক সাদাত হোসেন",
            courseId = "course_bilingual_daily",
            createdAt = 1700000010000L,
            wordCount = 185
        ),
        ArticleEntity(
            id = "art_sample_3",
            title = "Urban Echoes: The Symphony of the Modern Metropolis",
            content = """As twilight settles over the expansive metropolitan skyline, the city undergoes a remarkable aesthetic metamorphosis. The harsh daytime cacophony of screeching steel, construction drills, and blaring traffic horns begins to abate, gradually replaced by the melodic rhythm of evening jazz and distant laughter.

Street vendors close their kiosks with meticulous precision, having spent daylight hours entertaining garrulous commuters with witty conversation. In illuminated glass towers overhead, researchers and analysts pore over extensive datasets, formulating pragmatic solutions to streamline complex distribution networks. While certain conglomerates spend fortune on ostentatious monuments meant purely for corporate prestige, the true soul of the city thrives in its resilient neighborhood communities and artisan workshops.

To any perceptive observer, metropolitan life illustrates just how poignant and ephemeral each human interaction truly is. Hundreds of strangers cross paths on underground train platforms, exchange quiet nods, and dissolve into the nocturnal haze. Digital networks have made instant messaging ubiquitous, yet the irreplaceable warmth of face-to-face dialogue endures. Those who comprehend the beating heart of the modern city learn to venerate its diversity and share in the zealous pursuit of human progress.""".trimIndent(),
            author = "Elena Rostova",
            courseId = "course_ielts_academic",
            createdAt = 1700000020000L,
            wordCount = 205
        )
    )

    val sampleGames: List<GamePracticeEntity> = listOf(
        // Odd One Out
        GamePracticeEntity(
            id = "ooo_1",
            sheetType = "odd_one_out",
            question = "Which word does NOT belong with the others?",
            opt1 = "Diminish",
            opt2 = "Abate",
            opt3 = "Escalate#",
            opt4 = "Subside",
            answer = "Escalate",
            explanation = "'Escalate' means to increase, whereas Diminish, Abate, and Subside all mean to decrease or lessen."
        ),
        GamePracticeEntity(
            id = "ooo_2",
            sheetType = "odd_one_out",
            question = "Find the Odd One Out among the speech styles:",
            opt1 = "Garrulous",
            opt2 = "Taciturn#",
            opt3 = "Loquacious",
            opt4 = "Voluble",
            answer = "Taciturn",
            explanation = "'Taciturn' means reserved or quiet. The other three words mean talkative."
        ),
        GamePracticeEntity(
            id = "ooo_3",
            sheetType = "odd_one_out",
            question = "Identify the word that doesn't fit the pattern of duration:",
            opt1 = "Ephemeral",
            opt2 = "Transient",
            opt3 = "Perpetual#",
            opt4 = "Evanescent",
            answer = "Perpetual",
            explanation = "'Perpetual' means eternal or never-ending, while the others mean short-lived."
        ),
        // Analogy
        GamePracticeEntity(
            id = "ana_1",
            sheetType = "analogy",
            question = "METICULOUS : CARE :: ?",
            opt1 = "Garrulous : Silence",
            opt2 = "Zealous : Passion#",
            opt3 = "Ephemeral : Eternity",
            opt4 = "Ostentatious : Modesty",
            answer = "Zealous : Passion",
            explanation = "A meticulous person possesses care; a zealous person possesses passion (Characteristic relationship)."
        ),
        GamePracticeEntity(
            id = "ana_2",
            sheetType = "analogy",
            question = "CACOPHONY : SOUND :: ?",
            opt1 = "Odor : Smell",
            opt2 = "Glare : Light#",
            opt3 = "Music : Melody",
            opt4 = "Silence : Noise",
            answer = "Glare : Light",
            explanation = "Cacophony is an unpleasantly harsh sound; glare is unpleasantly harsh light."
        ),
        // Practice MCQs
        GamePracticeEntity(
            id = "prac_1",
            sheetType = "practice",
            question = "Which word best describes a situation where smartphones are found everywhere?",
            opt1 = "Ostentatious",
            opt2 = "Ubiquitous#",
            opt3 = "Reticent",
            opt4 = "Aberrant",
            answer = "Ubiquitous",
            explanation = "'Ubiquitous' means present, appearing, or found everywhere."
        ),
        GamePracticeEntity(
            id = "prac_2",
            sheetType = "practice",
            question = "Choose the closest synonym for 'PRAGMATIC':",
            opt1 = "Idealistic",
            opt2 = "Sensible & Practical#",
            opt3 = "Dreamy",
            opt4 = "Boastful",
            answer = "Sensible & Practical",
            explanation = "Pragmatic describes dealing with things sensibly and realistically."
        )
    )

    val sampleQuestionBank: List<QuestionBankEntity> = listOf(
        QuestionBankEntity(
            id = "qb_1",
            question = "Although the initial protests were turbulent, the uproar began to ______ after the council made major concessions.",
            opt1 = "amplify",
            opt2 = "abate#",
            opt3 = "burgeon",
            opt4 = "reverberate",
            answer = "abate",
            explanation = "The sentence requires a word meaning to lessen or decrease in intensity.",
            filter1 = "GRE High Frequency",
            filter2 = "Easy",
            filter3 = "Exam Prep",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        ),
        QuestionBankEntity(
            id = "qb_2",
            question = "The scientist's results were dismissed as an experimental ______ because no one could replicate them under standard conditions.",
            opt1 = "aberration#",
            opt2 = "fidelity",
            opt3 = "veracity",
            opt4 = "consolidation",
            answer = "aberration",
            explanation = "'Aberration' means a departure from what is normal, typical, or expected.",
            filter1 = "GRE High Frequency",
            filter2 = "Medium",
            filter3 = "Manhattan 500",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        ),
        QuestionBankEntity(
            id = "qb_3",
            question = "Far from being ______, Maya rarely spoke in group meetings unless directly asked a question.",
            opt1 = "reticent",
            opt2 = "garrulous#",
            opt3 = "scrupulous",
            opt4 = "austere",
            answer = "garrulous",
            explanation = "'Far from being garrulous' contrasts with rarely speaking.",
            filter1 = "Sentence Equivalence",
            filter2 = "Easy",
            filter3 = "Exam Prep",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        ),
        QuestionBankEntity(
            id = "qb_4",
            question = "The composer contrasted the delicate flute solo with a jarring ______ of drums and brass.",
            opt1 = "melody",
            opt2 = "harmony",
            opt3 = "cacophony#",
            opt4 = "eulogy",
            answer = "cacophony",
            explanation = "'Cacophony' means a harsh, discordant mixture of sounds.",
            filter1 = "Vocabulary Mastery",
            filter2 = "Medium",
            filter3 = "Oxford Core",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        ),
        QuestionBankEntity(
            id = "qb_5",
            question = "The politician's ______ display of luxury yachts and private jets drew harsh criticism during the economic downturn.",
            opt1 = "ostentatious#",
            opt2 = "subtle",
            opt3 = "penurious",
            opt4 = "ephemeral",
            answer = "ostentatious",
            explanation = "'Ostentatious' means characterized by vulgar or pretentious show in order to impress others.",
            filter1 = "GRE High Frequency",
            filter2 = "Hard",
            filter3 = "Manhattan 500",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        ),
        QuestionBankEntity(
            id = "qb_6",
            question = "The architect favoured ______ solutions that addressed real day-to-day user needs rather than abstract sculptural theories.",
            opt1 = "esoteric",
            opt2 = "pragmatic#",
            opt3 = "whimsical",
            opt4 = "hyperbolic",
            answer = "pragmatic",
            explanation = "'Pragmatic' means dealing with things realistically and practically.",
            filter1 = "Vocabulary Mastery",
            filter2 = "Easy",
            filter3 = "Exam Prep",
            filter1Label = "Category",
            filter2Label = "Difficulty",
            filter3Label = "Source"
        )
    )
}
