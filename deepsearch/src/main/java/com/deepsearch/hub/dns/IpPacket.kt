package com.deepsearch.hub.dns

/** تحليل وبناء حزم IPv4/UDP للاستعلامات القادمة من واجهة TUN. */
object IpPacket {

    class DnsQuery(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray,
    )

    /** يعيد null إذا لم تكن الحزمة IPv4/UDP موجهة للمنفذ 53. */
    fun parseDnsQuery(buf: ByteArray, len: Int): DnsQuery? {
        if (len < 28) return null
        if ((buf[0].toInt() ushr 4) and 0xF != 4) return null
        val ihl = (buf[0].toInt() and 0xF) * 4
        if (ihl < 20 || len < ihl + 8) return null
        if (buf[9].toInt() and 0xFF != 17) return null // UDP
        val total = minOf(u16(buf, 2), len)
        val dstPort = u16(buf, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(buf, ihl + 4)
        val end = minOf(total, ihl + udpLen)
        if (end <= ihl + 8) return null
        return DnsQuery(
            srcIp = buf.copyOfRange(12, 16),
            dstIp = buf.copyOfRange(16, 20),
            srcPort = u16(buf, ihl),
            dstPort = dstPort,
            payload = buf.copyOfRange(ihl + 8, end),
        )
    }

    /** يبني حزمة رد: يعكس العناوين والمنافذ ويضع رد DNS. */
    fun buildResponse(q: DnsQuery, dnsPayload: ByteArray): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        p[6] = 0x40 // Don't Fragment
        p[8] = 64 // TTL
        p[9] = 17 // UDP
        System.arraycopy(q.dstIp, 0, p, 12, 4)
        System.arraycopy(q.srcIp, 0, p, 16, 4)
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, q.dstPort)
        put16(p, 22, q.srcPort)
        put16(p, 24, udpLen)
        // checksum UDP = 0 مسموح في IPv4
        System.arraycopy(dnsPayload, 0, p, 28, dnsPayload.size)
        return p
    }

    fun checksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) {
            sum += u16(buf, i)
            i += 2
        }
        if (len % 2 == 1) sum += (buf[off + len - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }
}
