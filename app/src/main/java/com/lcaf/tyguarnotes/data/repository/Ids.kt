package com.lcaf.tyguarnotes.data.repository

import java.util.UUID

internal fun newId(): String = UUID.randomUUID().toString()
