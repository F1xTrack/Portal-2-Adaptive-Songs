package com.f1xtrack.portal2adaptivesongs.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalDestinationTest {

    @Test
    fun topLevelDestinations_haveExpectedOrder() {
        assertEquals(
            listOf(
                PortalDestination.Now,
                PortalDestination.Routes,
                PortalDestination.Library,
                PortalDestination.Profile,
            ),
            portalTopLevelDestinations,
        )
    }

    @Test
    fun routes_areUnique() {
        val routes = portalTopLevelDestinations.map { it.route }
        assertEquals(routes.distinct().size, routes.size)
        assertTrue(routes.all { it.isNotBlank() })
    }
}
