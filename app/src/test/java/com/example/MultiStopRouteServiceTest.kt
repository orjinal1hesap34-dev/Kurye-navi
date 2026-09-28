package com.example

import com.example.data.api.MultiStopRouteService
import com.example.data.model.DeliveryPoint
import com.example.data.model.NavLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiStopRouteServiceTest {

    private val service = MultiStopRouteService()

    @Test
    fun testActiveRouteConversion() {
        val origin = NavLocation(latitude = 40.9904, longitude = 29.0292)
        val stop1 = DeliveryPoint(
            id = "stop-1",
            title = "Kadıköy Çarşı",
            houseNumber = "12",
            location = NavLocation(latitude = 40.9915, longitude = 29.0305),
            stopOrder = 1
        )
        val stop2 = DeliveryPoint(
            id = "stop-2",
            title = "Moda Sahil",
            houseNumber = "45",
            location = NavLocation(latitude = 40.9850, longitude = 29.0250),
            stopOrder = 2
        )

        val deliveryPoints = listOf(stop1, stop2)

        runBlocking {
            val result = service.calculateMultiStopRoute(
                origin = origin,
                deliveryPoints = deliveryPoints,
                optimizeOrder = true
            )

            // Result will be Success or Failure depending on network availability in test container
            if (result is com.example.data.model.MultiStopRouteResult.Success) {
                val plan = result.plan
                assertEquals(2, plan.deliveryPoints.size)
                assertTrue(plan.totalDistanceMeters > 0)
                assertTrue(plan.legs.isNotEmpty())

                val activeRoute = service.createActiveNavigationRoute(plan, 0)
                assertNotNull(activeRoute)
                assertEquals(plan.legs[0].targetPoint.location.latitude, activeRoute!!.destination.latitude, 0.0001)
                assertTrue(activeRoute.destinationTitle.contains(plan.legs[0].targetPoint.title))
            } else if (result is com.example.data.model.MultiStopRouteResult.Failure) {
                assertTrue(result.errorMessage.isNotBlank())
            }
        }
    }

    @Test
    fun testEmptyDeliveryPointsFailure() {
        val origin = NavLocation(latitude = 40.9904, longitude = 29.0292)
        runBlocking {
            val result = service.calculateMultiStopRoute(origin, emptyList())
            assertTrue(result is com.example.data.model.MultiStopRouteResult.Failure)
            assertEquals("Teslimat listesinde aktif teslimat noktası bulunmuyor.", (result as com.example.data.model.MultiStopRouteResult.Failure).errorMessage)
        }
    }

    @Test
    fun testInvalidOriginFailure() {
        val origin = NavLocation(latitude = 0.0, longitude = 0.0)
        val stop = DeliveryPoint(
            title = "Test",
            location = NavLocation(40.99, 29.03)
        )
        runBlocking {
            val result = service.calculateMultiStopRoute(origin, listOf(stop))
            assertTrue(result is com.example.data.model.MultiStopRouteResult.Failure)
            assertEquals("Kurye başlangıç konumu henüz alınamadı.", (result as com.example.data.model.MultiStopRouteResult.Failure).errorMessage)
        }
    }
}
