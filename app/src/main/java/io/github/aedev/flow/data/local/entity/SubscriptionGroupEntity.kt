package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_groups")
data class SubscriptionGroupEntity(
    @PrimaryKey val name: String,
    val channelIds: String = "",
    val sortOrder: Int = 0
)
