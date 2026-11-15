package {{packageName}}.core.domain.model

/** Profil des Nutzers; [dailyGoal] = gewünschte erledigte Aufgaben pro Tag. */
data class Profile(val name: String = "", val dailyGoal: Int = 3)
