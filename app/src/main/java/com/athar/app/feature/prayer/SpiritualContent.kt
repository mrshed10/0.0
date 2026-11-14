package com.athar.app.feature.prayer

import java.time.LocalDate
import java.util.Locale

/** A dhikr with its customary count. */
data class Dhikr(val ar: String, val en: String, val count: Int)

/**
 * Bundled texts (offline). Quran verses are written without diacritics;
 * for a larger library, import a verified source such as Tanzil.
 */
object SpiritualContent {
    val tasbihPresets = listOf(
        Dhikr("سبحان الله", "SubhanAllah", 33),
        Dhikr("الحمد لله", "Alhamdulillah", 33),
        Dhikr("الله أكبر", "Allahu Akbar", 34),
        Dhikr("لا إله إلا الله", "La ilaha illa Allah", 100),
        Dhikr("أستغفر الله", "Astaghfirullah", 100),
        Dhikr("سبحان الله وبحمده", "SubhanAllahi wa bihamdih", 100),
        Dhikr("اللهم صل وسلم على نبينا محمد", "Salawat upon the Prophet ﷺ", 10),
    )

    val morning = listOf(
        Dhikr("أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "We have entered the morning and all sovereignty belongs to Allah…", 1),
        Dhikr("اللهم بك أصبحنا، وبك أمسينا، وبك نحيا، وبك نموت، وإليك النشور", "O Allah, by You we enter the morning and the evening, by You we live and die, and to You is the resurrection.", 1),
        Dhikr("بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "In the name of Allah, with whose name nothing on earth or in heaven can cause harm…", 3),
        Dhikr("رضيت بالله ربا، وبالإسلام دينا، وبمحمد صلى الله عليه وسلم نبيا", "I am pleased with Allah as Lord, Islam as religion and Muhammad ﷺ as Prophet.", 3),
        Dhikr("حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم", "Allah is sufficient for me; there is no god but Him…", 7),
        Dhikr("سبحان الله وبحمده", "Glory be to Allah and praise Him.", 100),
        Dhikr("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "None has the right to be worshipped but Allah alone…", 10),
        Dhikr("أستغفر الله وأتوب إليه", "I seek Allah's forgiveness and turn to Him in repentance.", 100),
        Dhikr("اللهم صل وسلم على نبينا محمد", "O Allah, send prayers and peace upon our Prophet Muhammad.", 10),
    )

    val evening = listOf(
        Dhikr("أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "We have entered the evening and all sovereignty belongs to Allah…", 1),
        Dhikr("اللهم بك أمسينا، وبك أصبحنا، وبك نحيا، وبك نموت، وإليك المصير", "O Allah, by You we enter the evening and the morning, by You we live and die, and to You is the return.", 1),
        Dhikr("أعوذ بكلمات الله التامات من شر ما خلق", "I seek refuge in the perfect words of Allah from the evil of what He created.", 3),
        Dhikr("بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "In the name of Allah, with whose name nothing on earth or in heaven can cause harm…", 3),
        Dhikr("رضيت بالله ربا، وبالإسلام دينا، وبمحمد صلى الله عليه وسلم نبيا", "I am pleased with Allah as Lord, Islam as religion and Muhammad ﷺ as Prophet.", 3),
        Dhikr("حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم", "Allah is sufficient for me; there is no god but Him…", 7),
        Dhikr("سبحان الله وبحمده", "Glory be to Allah and praise Him.", 100),
        Dhikr("أستغفر الله وأتوب إليه", "I seek Allah's forgiveness and turn to Him in repentance.", 100),
    )

    data class Verse(val ar: String, val en: String, val refAr: String, val refEn: String)

    val verses = listOf(
        Verse("فاذكروني أذكركم واشكروا لي ولا تكفرون", "So remember Me; I will remember you. Be grateful to Me and do not deny Me.", "البقرة ١٥٢", "Al-Baqarah 152"),
        Verse("ألا بذكر الله تطمئن القلوب", "Surely in the remembrance of Allah do hearts find rest.", "الرعد ٢٨", "Ar-Ra'd 28"),
        Verse("فإن مع العسر يسرا", "So surely with hardship comes ease.", "الشرح ٥", "Ash-Sharh 5"),
        Verse("وقال ربكم ادعوني أستجب لكم", "Your Lord has said: Call upon Me; I will respond to you.", "غافر ٦٠", "Ghafir 60"),
        Verse("ومن يتوكل على الله فهو حسبه", "Whoever relies upon Allah, He is sufficient for him.", "الطلاق ٣", "At-Talaq 3"),
        Verse("لا يكلف الله نفسا إلا وسعها", "Allah does not burden a soul beyond what it can bear.", "البقرة ٢٨٦", "Al-Baqarah 286"),
        Verse("وإذا سألك عبادي عني فإني قريب", "When My servants ask you about Me, I am indeed near.", "البقرة ١٨٦", "Al-Baqarah 186"),
        Verse("رب اشرح لي صدري ويسر لي أمري", "My Lord, expand for me my chest and ease for me my task.", "طه ٢٥-٢٦", "Taha 25-26"),
        Verse("وما توفيقي إلا بالله", "My success is only through Allah.", "هود ٨٨", "Hud 88"),
        Verse("ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار", "Our Lord, give us good in this world and good in the Hereafter, and protect us from the Fire.", "البقرة ٢٠١", "Al-Baqarah 201"),
        Verse("حسبنا الله ونعم الوكيل", "Allah is sufficient for us, and He is the best Disposer of affairs.", "آل عمران ١٧٣", "Al Imran 173"),
        Verse("وهو معكم أين ما كنتم", "And He is with you wherever you are.", "الحديد ٤", "Al-Hadid 4"),
        Verse("واصبر وما صبرك إلا بالله", "Be patient, and your patience is only through Allah.", "النحل ١٢٧", "An-Nahl 127"),
        Verse("إن الله مع الصابرين", "Indeed, Allah is with the patient.", "البقرة ١٥٣", "Al-Baqarah 153"),
    )

    fun verseFor(date: LocalDate): Verse = verses[(date.toEpochDay() % verses.size).toInt().let { if (it < 0) it + verses.size else it }]

    fun text(d: Dhikr, locale: Locale) = if (locale.language == "ar") d.ar else d.en
}
