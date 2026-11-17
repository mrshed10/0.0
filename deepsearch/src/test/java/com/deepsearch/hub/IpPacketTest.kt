package com.deepsearch.hub

import com.deepsearch.hub.dns.IpPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpPacketTest {

    private fun query(dstPort: Int = 53, payload: ByteArray = byteArrayOf(0x12, 0x34, 1, 0)): ByteArray {
        val q = IpPacket.DnsQuery(
            srcIp = byteArrayOf(10, 111, 222.toByte(), 2), // نبني "رداً" ثم نعكسه لنحصل على استعلام
            dstIp = byteArrayOf(10, 111, 222.toByte(), 1),
            srcPort = dstPort,
            dstPort = 40000,
            payload = payload,
        )
        return IpPacket.buildResponse(q, payload)
    }

    @Test
    fun parsesDnsQuery() {
        val pkt = query()
        val q = IpPacket.parseDnsQuery(pkt, pkt.size)
        assertNotNull(q)
        assertEquals(40000, q!!.srcPort)
        assertEquals(53, q.dstPort)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 1, 0), q.payload)
        assertArrayEquals(byteArrayOf(10, 111, 222.toByte(), 2), q.dstIp)
    }

    @Test
    fun ignoresNonDnsPorts() {
        val pkt = query(dstPort = 443)
        assertNull(IpPacket.parseDnsQuery(pkt, pkt.size))
    }

    @Test
    fun responseSwapsAddressesAndHasValidChecksum() {
        val pkt = query()
        val q = IpPacket.parseDnsQuery(pkt, pkt.size)!!
        val answer = ByteArray(40) { it.toByte() }
        val resp = IpPacket.buildResponse(q, answer)
        assertEquals(20 + 8 + answer.size, resp.size)
        // checksum الخاص بالرأس يجب أن يعطي صفراً عند إعادة الحساب
        assertEquals(0, IpPacket.checksum(resp, 0, 20))
        assertArrayEquals(q.dstIp, resp.copyOfRange(12, 16))
        assertArrayEquals(q.srcIp, resp.copyOfRange(16, 20))
        assertEquals(53, ((resp[20].toInt() and 0xFF) shl 8) or (resp[21].toInt() and 0xFF))
    }
}
