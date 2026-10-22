package com.khumam.gbaduo.companion
import org.junit.Assert.*
import org.junit.Test
class LatestCompanionSnapshotTest {
 @Test fun newestDataWinsAndEarlierNavigationIsRetainedUntilDrawn() {
  val q=LatestCompanionSnapshot()
  q.offer(mapOf("hp" to 10,"ui" to mapOf("set" to mapOf("mainTab" to "Dialogue","selected" to 1))))
  q.offer(mapOf("hp" to 9,"ui" to mapOf("set" to mapOf("selected" to 2))))
  q.offer(mapOf("hp" to 8))
  val value=q.take()!!
  assertEquals(8,value["hp"])
  val set=(value["ui"] as Map<*,*>)["set"] as Map<*,*>
  assertEquals("Dialogue",set["mainTab"]); assertEquals(2,set["selected"])
  assertNull(q.take())
 }
 @Test fun consumedNavigationDoesNotReplayOnTheNextFrame() {
  val q=LatestCompanionSnapshot();q.offer(mapOf("ui" to mapOf("set" to mapOf("page" to "detail"))));q.take()
  q.offer(mapOf("hp" to 4));assertFalse(q.take()!!.containsKey("ui"))
 }
}
