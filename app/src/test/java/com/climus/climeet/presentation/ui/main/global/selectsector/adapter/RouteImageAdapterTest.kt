package com.climus.climeet.presentation.ui.main.global.selectsector.adapter

import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteImageAdapterTest {
    @Test
    fun sameSector_differentRoutes_haveDifferentIdentity() {
        val first = RouteUiData(routeId = 1, sectorId = 101) {}

        assertFalse(RouteImageAdapter.diffCallback.areItemsTheSame(first, first.copy(routeId = 2)))
    }

    @Test
    fun sameRoute_changedContent_keepsIdentityAndUpdatesContent() {
        val first = RouteUiData(routeId = 1, sectorId = 101) {}
        val updated = first.copy(sectorId = 102, routeImg = "updated.png")

        assertTrue(RouteImageAdapter.diffCallback.areItemsTheSame(first, updated))
        assertFalse(RouteImageAdapter.diffCallback.areContentsTheSame(first, updated))
        assertTrue(RouteImageAdapter.diffCallback.areContentsTheSame(first, first.copy()))
    }
}
