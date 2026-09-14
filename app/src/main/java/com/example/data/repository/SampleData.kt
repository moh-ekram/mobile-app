package com.example.data.repository

import com.example.data.model.GamePracticeEntity
import com.example.data.model.QuestionBankEntity
import com.example.data.model.VocabularyWordEntity

object SampleData {

    val sampleWords: List<VocabularyWordEntity> = listOf(
        VocabularyWordEntity(
            id = "word_1",
            word = "Abate",
            meaning = "To lessen in intensity or degree; subside",
            group = "1",
            synonyms = "Subside, diminish, lessen, decline, curtail",
            extraWord = "Abatement (noun): The abatement of the storm brought relief.",
            example = "The storm suddenly began to abate as dawn broke across the valley.",
            mnemonic = "Think of 'a-bate': eating food will 'abate' your hunger.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_2",
            word = "Aberrant",
            meaning = "Deviating from the normal, usual, or expected",
            group = "1",
            synonyms = "Abnormal, anomalous, deviant, divergent, erratic",
            extraWord = "Aberration (noun): The violent surge was an aberration in the quiet town.",
            example = "His aberrant behavior at the symposium drew perplexed glances from peers.",
            mnemonic = "Aberrant sounds like 'a bear errant' wandering away from normal paths.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_3",
            word = "Cacophony",
            meaning = "A harsh, discordant mixture of sounds",
            group = "1",
            synonyms = "Dissonance, clamor, racket, discord, din",
            extraWord = "Cacophonous (adj): A cacophonous sound of construction drills outside.",
            example = "The bustling street market was filled with a cacophony of vendors and horns.",
            mnemonic = "Caco (bad) + phone (sound) = unpleasant jarring noise.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_4",
            word = "Ephemeral",
            meaning = "Lasting for a very short time; fleeting",
            group = "1",
            synonyms = "Fleeting, transient, evanescent, short-lived, momentary",
            extraWord = "Ephemera (noun): Collecting historical ephemera like concert tickets.",
            example = "The golden sunset was gorgeous but ephemeral, disappearing in minutes.",
            mnemonic = "Sounds like 'funeral' - life's moments are short and fleeting.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_5",
            word = "Garrulous",
            meaning = "Excessively talkative, especially on trivial matters",
            group = "1",
            synonyms = "Loquacious, talkative, voluble, chatty, verbose",
            extraWord = "Garrulity (noun): His endless garrulity tested everyone's patience.",
            example = "The garrulous driver narrated his entire life story during the brief trip.",
            mnemonic = "Girls & boys chatty in a 'garage' -> garrulous.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_6",
            word = "Meticulous",
            meaning = "Showing great attention to detail; very careful and precise",
            group = "1",
            synonyms = "Scrupulous, punctilious, painstaking, fastidious, thorough",
            extraWord = "Meticulousness (noun): Meticulousness is required when analyzing data.",
            example = "She was meticulous about keeping her lab notebooks error-free.",
            mnemonic = "Meticulous = 'Medal for every tiny detail'.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_7",
            word = "Ostentatious",
            meaning = "Characterized by pretentious or showy display to impress",
            group = "2",
            synonyms = "Pretentious, flamboyant, showy, gaudy, conspicuous",
            extraWord = "Ostentation (noun): Displaying gold watches purely for ostentation.",
            example = "The tycoon arrived in an ostentatious gilded carriage to make a scene.",
            mnemonic = "O-STUNT-tatious: doing stunts to show off.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_8",
            word = "Pragmatic",
            meaning = "Dealing with things sensibly and realistically",
            group = "2",
            synonyms = "Practical, realistic, sensible, rational, utilitarian",
            extraWord = "Pragmatism (noun): He approached corporate governance with pragmatism.",
            example = "Instead of wishful thinking, she devised a pragmatic strategy for cost cutting.",
            mnemonic = "Pragmatic sounds like Practical Mathematics.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_9",
            word = "Reticent",
            meaning = "Not revealing one's thoughts or feelings readily; reserved",
            group = "2",
            synonyms = "Reserved, taciturn, quiet, introverted, guarded",
            extraWord = "Reticence (noun): Her reticence was often mistaken for haughtiness.",
            example = "He remained reticent about his private life despite probing questions.",
            mnemonic = "Re-ticket: keeping silent when asked for a ticket.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_10",
            word = "Ubiquitous",
            meaning = "Present, appearing, or found everywhere; omnipresent",
            group = "2",
            synonyms = "Omnipresent, pervasive, everywhere, universal, rampant",
            extraWord = "Ubiquity (noun): The ubiquity of smartphones changed modern social dynamics.",
            example = "Coffee shops have become ubiquitous in almost every major downtown.",
            mnemonic = "Mosquitoes 'bite us' everywhere: U-biquitous.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_11",
            word = "Venerate",
            meaning = "To regard with great respect; revere or honor",
            group = "3",
            synonyms = "Revere, worship, respect, idolize, honor",
            extraWord = "Venerable (adj): The venerable professor received a standing ovation.",
            example = "Many ancient cultures venerate nature as sacred and divine.",
            mnemonic = "Ven-rate: when you rate someone highest with great respect.",
            status = "unrated"
        ),
        VocabularyWordEntity(
            id = "word_12",
            word = "Zealous",
            meaning = "Having or showing great energy, enthusiasm, or fervor",
            group = "3",
            synonyms = "Fervent, passionate, ardent, avid, dedicated",
            extraWord = "Zealot (noun): He was an uncompromising zealot for constitutional reform.",
            example = "The zealous volunteers planted thousands of trees across the hillside.",
            mnemonic = "Full of ZEAL (enthusiasm).",
            status = "unrated"
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
