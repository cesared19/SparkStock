package com.sparkstock.service.services

class NotFoundException(message: String) : RuntimeException(message)

/** The request is valid but the device's current state doesn't allow it, e.g. checking out a device on loan. */
class ConflictException(message: String) : RuntimeException(message)

class ForbiddenException(message: String) : RuntimeException(message)

/** Something needs a setting that's empty, e.g. STOCK_INTEGRATION_TOKEN. */
class NotConfiguredException(message: String) : RuntimeException(message)
