package com.sparkstock.service.services

import com.sparkstock.service.models.DeviceStatus
import java.util.Locale

enum class Lang(val locale: Locale) {
    EN(Locale.ENGLISH), ES(Locale.forLanguageTag("es"));

    companion object {
        /** From an Accept-Language header or a stored value: Spanish when it starts with "es", English otherwise. */
        fun parse(value: String?): Lang =
            if (value?.trim()?.lowercase()?.startsWith("es") == true) ES else EN
    }
}

/**
 * Every message SparkStock shows people, in English and Spanish. The language of a request comes from its
 * Accept-Language header (the client sends the person's choice); outside requests, e.g. reminders, it's passed in.
 */
object Messages {
    private val current = ThreadLocal<Lang?>()

    fun lang(): Lang = current.get() ?: Lang.EN

    /** The language the request asked for, null when it sent no Accept-Language (e.g. another app). */
    fun requested(): Lang? = current.get()

    fun set(lang: Lang) = current.set(lang)

    fun clear() = current.remove()

    fun t(key: String, vararg args: Any?): String = t(lang(), key, *args)

    fun t(lang: Lang, key: String, vararg args: Any?): String {
        val template = (if (lang == Lang.ES) ES[key] else null) ?: EN[key] ?: key
        return if (args.isEmpty()) template else String.format(lang.locale, template, *args)
    }

    fun status(status: DeviceStatus, lang: Lang = lang()) = t(lang, "status.${status.name}")

    private val EN = mapOf(
        "status.AVAILABLE" to "available",
        "status.ON_LOAN" to "on loan",
        "status.ASSIGNED" to "assigned",
        "status.IN_REPAIR" to "in repair",
        "status.LOST" to "lost",
        "status.RETIRED" to "retired",
        // web
        "web.sessionExpired" to "Your session has expired, sign in again",
        "web.adminsOnly" to "Only admins can do this",
        "web.integrationOff" to "The integration API is off: set STOCK_INTEGRATION_TOKEN",
        "web.integrationToken" to "Send the integration token as Authorization: Bearer <token>",
        "web.badRequest" to "The request isn't valid JSON, or a value has the wrong type",
        "web.stale" to "Someone else changed this device at the same time, reload and try again",
        "web.sparkytcDown" to "SparkyTC isn't answering, try again in a moment",
        "web.invalid" to "Invalid request",
        // sign-in
        "auth.enterCredentials" to "Enter your SparkyTC username and password",
        "auth.disabled" to "This account is disabled",
        "auth.rejected" to "SparkyTC rejected the sign-in",
        "auth.invalid" to "Invalid username or password",
        // devices
        "device.notFound" to "Device not found",
        "device.noTag" to "No device has the tag %s",
        "device.chooseType" to "Choose a device type",
        "type.notFound" to "Device type not found",
        "type.archived" to "%s is archived, new devices can't use it",
        "device.tagFormat" to "Asset tags use letters, digits, '.', '_' and '-'",
        "device.tagUsed" to "The tag %s is already used",
        "device.typeFixed" to "A device's type can't be changed",
        "device.enterModel" to "Enter the model",
        "device.serialUsed" to "%s already has the serial number %s",
        "device.warrantyBeforePurchase" to "The warranty can't end before the purchase date",
        "device.projectTooLong" to "The project code is too long",
        "loan.assignedType" to "%s devices are assigned by an admin, not lent",
        "loan.dueInPast" to "The return date must be in the future",
        "loan.tooLong" to "A loan can last %d days at most",
        "loan.notOnLoan" to "%s isn't on loan",
        "loan.onlyBorrower" to "Only %s or an admin can return %s",
        "loan.theBorrower" to "the borrower",
        "assign.lentType" to "%s devices are lent, not assigned",
        "assign.choosePerson" to "Choose who gets the device",
        "user.notFound" to "User not found",
        "assign.disabled" to "%s's account is disabled",
        "assign.already" to "%s is already assigned to %s",
        "assign.notAssigned" to "%s isn't assigned",
        "status.choose" to "Choose a status",
        "status.useFlows" to "Use check-out or assign to give a device to someone",
        "status.already" to "%s is already %s",
        "unavailable.onLoan" to "%s is on loan to %s",
        "unavailable.until" to " until %s",
        "unavailable.assigned" to "%s is assigned to %s",
        "unavailable.other" to "%s is %s",
        // catalog
        "catalog.enterName" to "Enter a name",
        "type.nameUsed" to "There is already a device type called %s",
        "type.prefixFormat" to "The tag prefix must be 1 to 8 letters or digits, starting with a letter",
        "type.prefixUsed" to "%s already uses the prefix %s",
        "type.coverageField" to "The coverage field must be one of the type's fields",
        "location.notFound" to "Location not found",
        "location.nameUsed" to "There is already a location called %s",
        "field.keyFormat" to "Field key \"%s\" must be lowercase letters, digits or _ and start with a letter",
        "field.keyTwice" to "Field key \"%s\" is used twice",
        "field.needsLabel" to "Field \"%s\" needs a label",
        "field.needsOption" to "Field \"%s\" needs at least one option",
        "field.required" to "%s is required",
        "field.tooLong" to "%s is too long",
        "field.number" to "%s must be a number",
        "field.boolean" to "%s must be yes or no",
        "field.date" to "%s must be a date (YYYY-MM-DD)",
        "field.option" to "%s must be one of: %s",
        // import
        "import.noRows" to "The file has no rows",
        "import.tooMany" to "Import at most %d rows at a time",
        "import.invalidRow" to "Invalid row",
        "import.sameId" to "Another row has the same ID",
        "import.sameSerial" to "Another row has the same serial number",
        "import.noModel" to "The row has no model nor name",
        "import.otherType" to "%s is a %s, not a %s",
        "import.noType" to "The row has no type",
        "import.changes" to "Changes: %s",
        "import.versionNote" to "Version: %s",
        "import.accessoriesNote" to "Accessories: %s",
        // coverage
        "coverage.needsToken" to "The coverage matrix needs STOCK_INTEGRATION_TOKEN, the same secret as in SparkyTC",
        "coverage.chooseProject" to "Choose a project",
        "coverage.enterModel" to "Enter the model, e.g. Focus 3",
        "coverage.versionTooLong" to "The version is too long",
        "coverage.targetExists" to "%s is already a target of %s",
        "coverage.targetNotFound" to "Target not found",
        // reports
        "report.period" to "The period must be 30, 90, 180, 365 or all",
        // reminders (in the person's language)
        "reminder.noToken" to "STOCK_INTEGRATION_TOKEN is not set",
        "reminder.overdue.title" to "Return %s: it was due on %s",
        "reminder.overdue.message" to "%s is overdue. Return it in SparkStock, or ask an admin if you need it longer.",
        "reminder.dueSoon.title" to "%s is due back on %s",
        "reminder.dueSoon.message" to "Remember to return %s in SparkStock when you're done with it.",
        "reminder.warranty.title" to "The warranty of %s ends on %s",
        "reminder.warranty.message" to "%s: check it before the warranty ends.",
        "reminder.escalation.title" to "%s hasn't been returned: %d days late",
        "reminder.escalation.message" to "%s has had %s since %s; it was due on %s.",
        "date.pattern" to "MMM d, yyyy",
    )

    private val ES = mapOf(
        "status.AVAILABLE" to "disponible",
        "status.ON_LOAN" to "prestado",
        "status.ASSIGNED" to "asignado",
        "status.IN_REPAIR" to "en reparación",
        "status.LOST" to "perdido",
        "status.RETIRED" to "dado de baja",
        "web.sessionExpired" to "Tu sesión expiró, vuelve a entrar",
        "web.adminsOnly" to "Solo los admins pueden hacer esto",
        "web.integrationOff" to "La API de integración está apagada: configura STOCK_INTEGRATION_TOKEN",
        "web.integrationToken" to "Envía el token de integración como Authorization: Bearer <token>",
        "web.badRequest" to "La solicitud no es JSON válido, o un valor tiene el tipo equivocado",
        "web.stale" to "Alguien más cambió este dispositivo al mismo tiempo, recarga e intenta de nuevo",
        "web.sparkytcDown" to "SparkyTC no responde, intenta de nuevo en un momento",
        "web.invalid" to "Solicitud no válida",
        "auth.enterCredentials" to "Escribe tu usuario y contraseña de SparkyTC",
        "auth.disabled" to "Esta cuenta está desactivada",
        "auth.rejected" to "SparkyTC rechazó el inicio de sesión",
        "auth.invalid" to "Usuario o contraseña incorrectos",
        "device.notFound" to "No se encontró el dispositivo",
        "device.noTag" to "Ningún dispositivo tiene la etiqueta %s",
        "device.chooseType" to "Elige un tipo de dispositivo",
        "type.notFound" to "No se encontró el tipo de dispositivo",
        "type.archived" to "%s está archivado, no se pueden agregar dispositivos de ese tipo",
        "device.tagFormat" to "Las etiquetas usan letras, dígitos, '.', '_' y '-'",
        "device.tagUsed" to "La etiqueta %s ya está en uso",
        "device.typeFixed" to "El tipo de un dispositivo no se puede cambiar",
        "device.enterModel" to "Escribe el modelo",
        "device.serialUsed" to "%s ya tiene el número de serie %s",
        "device.warrantyBeforePurchase" to "La garantía no puede terminar antes de la fecha de compra",
        "device.projectTooLong" to "El código de proyecto es demasiado largo",
        "loan.assignedType" to "Los dispositivos %s los asigna un admin, no se prestan",
        "loan.dueInPast" to "La fecha de devolución debe ser futura",
        "loan.tooLong" to "Un préstamo puede durar como máximo %d días",
        "loan.notOnLoan" to "%s no está prestado",
        "loan.onlyBorrower" to "Solo %s o un admin pueden devolver %s",
        "loan.theBorrower" to "quien lo tiene",
        "assign.lentType" to "Los dispositivos %s se prestan, no se asignan",
        "assign.choosePerson" to "Elige a quién se le asigna",
        "user.notFound" to "No se encontró el usuario",
        "assign.disabled" to "La cuenta de %s está desactivada",
        "assign.already" to "%s ya está asignado a %s",
        "assign.notAssigned" to "%s no está asignado",
        "status.choose" to "Elige un estado",
        "status.useFlows" to "Usa préstamo o asignación para darle un dispositivo a alguien",
        "status.already" to "%s ya está %s",
        "unavailable.onLoan" to "%s está prestado a %s",
        "unavailable.until" to " hasta el %s",
        "unavailable.assigned" to "%s está asignado a %s",
        "unavailable.other" to "%s está %s",
        "catalog.enterName" to "Escribe un nombre",
        "type.nameUsed" to "Ya existe un tipo de dispositivo llamado %s",
        "type.prefixFormat" to "El prefijo debe tener de 1 a 8 letras o dígitos y empezar con una letra",
        "type.prefixUsed" to "%s ya usa el prefijo %s",
        "type.coverageField" to "El campo de cobertura debe ser uno de los campos del tipo",
        "location.notFound" to "No se encontró la ubicación",
        "location.nameUsed" to "Ya existe una ubicación llamada %s",
        "field.keyFormat" to "La clave \"%s\" debe tener minúsculas, dígitos o _ y empezar con una letra",
        "field.keyTwice" to "La clave \"%s\" está repetida",
        "field.needsLabel" to "El campo \"%s\" necesita un nombre",
        "field.needsOption" to "El campo \"%s\" necesita al menos una opción",
        "field.required" to "%s es obligatorio",
        "field.tooLong" to "%s es demasiado largo",
        "field.number" to "%s debe ser un número",
        "field.boolean" to "%s debe ser sí o no",
        "field.date" to "%s debe ser una fecha (AAAA-MM-DD)",
        "field.option" to "%s debe ser una de: %s",
        "import.noRows" to "El archivo no tiene filas",
        "import.tooMany" to "Importa como máximo %d filas a la vez",
        "import.invalidRow" to "Fila no válida",
        "import.sameId" to "Otra fila tiene el mismo ID",
        "import.sameSerial" to "Otra fila tiene el mismo número de serie",
        "import.noModel" to "La fila no tiene modelo ni nombre",
        "import.otherType" to "%s es un %s, no un %s",
        "import.noType" to "La fila no tiene tipo",
        "import.changes" to "Cambios: %s",
        "import.versionNote" to "Versión: %s",
        "import.accessoriesNote" to "Accesorios: %s",
        "coverage.needsToken" to "La matriz de cobertura necesita STOCK_INTEGRATION_TOKEN, el mismo secreto que en SparkyTC",
        "coverage.chooseProject" to "Elige un proyecto",
        "coverage.enterModel" to "Escribe el modelo, p. ej. Focus 3",
        "coverage.versionTooLong" to "La versión es demasiado larga",
        "coverage.targetExists" to "%s ya es un objetivo de %s",
        "coverage.targetNotFound" to "No se encontró el objetivo",
        "report.period" to "El periodo debe ser 30, 90, 180, 365 o all",
        "reminder.noToken" to "STOCK_INTEGRATION_TOKEN no está configurado",
        "reminder.overdue.title" to "Devuelve %s: vencía el %s",
        "reminder.overdue.message" to "%s está atrasado. Devuélvelo en SparkStock, o pide más tiempo a un admin.",
        "reminder.dueSoon.title" to "%s vence el %s",
        "reminder.dueSoon.message" to "Recuerda devolver %s en SparkStock cuando termines de usarlo.",
        "reminder.warranty.title" to "La garantía de %s termina el %s",
        "reminder.warranty.message" to "%s: revísalo antes de que termine la garantía.",
        "reminder.escalation.title" to "%s no se ha devuelto: %d días de atraso",
        "reminder.escalation.message" to "%s tiene %s desde el %s; vencía el %s.",
        "date.pattern" to "d 'de' MMM 'de' yyyy",
    )
}
