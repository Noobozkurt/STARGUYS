package com.starguys.battle.net

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A game found on the local network or over Bluetooth. */
class FoundHost(val key: String, val name: String, val detail: String, @Volatile var seen: Long, val connect: () -> Link)

// ============================================================================ Wi-Fi (LAN)
object Lan {
    const val TCP_PORT = 47777
    const val UDP_PORT = 47778

    fun localIp(): String {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) return a.hostAddress ?: ""
            }
        } catch (_: Throwable) { }
        return "?"
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) ia.broadcast?.let { out.add(it) }
            }
        } catch (_: Throwable) { }
        try { out.add(InetAddress.getByName("255.255.255.255")) } catch (_: Throwable) { }
        return out
    }

    /** Hosts a game: accepts TCP connections and announces itself with UDP broadcasts. */
    class Host(val session: HostSession, val port: Int = TCP_PORT) : AutoCloseable {
        @Volatile private var running = true
        val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
        val boundPort get() = server.localPort

        init {
            Thread({
                while (running) {
                    try {
                        val s = server.accept()
                        s.tcpNoDelay = true
                        session.onLink(Link("tcp-${s.inetAddress.hostAddress}", s.getInputStream(), s.getOutputStream()) { s.close() })
                    } catch (_: Throwable) { if (!running) break }
                }
            }, "lan-accept").apply { isDaemon = true }.start()
            Thread({
                var sock: DatagramSocket? = null
                try {
                    sock = DatagramSocket().apply { broadcast = true }
                    while (running) {
                        val host = session.lobby.players.firstOrNull()?.name ?: "Hôte"
                        val msg = "SGB${Msg.VERSION}|$host|${session.lobby.mode}|${session.playerCount}|${boundPort}".toByteArray()
                        for (a in broadcastAddresses()) try { sock.send(DatagramPacket(msg, msg.size, a, UDP_PORT)) } catch (_: Throwable) { }
                        Thread.sleep(1000)
                    }
                } catch (_: Throwable) { }
                sock?.close()
            }, "lan-beacon").apply { isDaemon = true }.start()
        }

        override fun close() { running = false; try { server.close() } catch (_: Throwable) { } }
    }

    /** Listens for host announcements. */
    class Finder(ctx: Context?) : AutoCloseable {
        val hosts = ConcurrentHashMap<String, FoundHost>()
        @Volatile private var running = true
        private var sock: DatagramSocket? = null
        private val lock: WifiManager.MulticastLock? = try {
            (ctx?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.createMulticastLock("starguys")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) { null }

        init {
            Thread({
                try {
                    val s = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(UDP_PORT)) }
                    sock = s
                    val buf = ByteArray(512)
                    while (running) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val txt = String(p.data, 0, p.length)
                        val parts = txt.split("|")
                        if (parts.size < 5 || parts[0] != "SGB${Msg.VERSION}") continue
                        val ip = p.address.hostAddress ?: continue
                        val port = parts[4].toIntOrNull() ?: TCP_PORT
                        val mode = parts[2].toIntOrNull() ?: 0
                        val key = "$ip:$port"
                        val modeName = com.starguys.battle.modes.Modes.def(mode).name
                        val fh = hosts[key]
                        if (fh != null) fh.seen = System.currentTimeMillis()
                        else hosts[key] = FoundHost(key, parts[1], "$modeName · ${parts[3]} joueur(s) · $ip", System.currentTimeMillis()) { connect(ip, port) }
                    }
                } catch (_: Throwable) { }
            }, "lan-find").apply { isDaemon = true }.start()
        }

        override fun close() {
            running = false
            try { sock?.close() } catch (_: Throwable) { }
            try { lock?.release() } catch (_: Throwable) { }
        }
    }

    /** Blocking: call from a background thread. */
    fun connect(ip: String, port: Int = TCP_PORT): Link {
        val s = Socket()
        s.connect(InetSocketAddress(ip, port), 5000)
        s.tcpNoDelay = true
        return Link("tcp-$ip", s.getInputStream(), s.getOutputStream()) { s.close() }
    }
}

// ============================================================================ Bluetooth (RFCOMM)
@SuppressLint("MissingPermission")
object Bt {
    val UUID_GAME: UUID = UUID.fromString("8f3c2a1e-5b7d-4c9a-9e2f-1a6b3c5d7e90")

    fun adapter(ctx: Context): BluetoothAdapter? = try {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    } catch (_: Throwable) { null }

    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE")
    else arrayOf("android.permission.ACCESS_FINE_LOCATION")

    fun hasPermissions(ctx: Context): Boolean = permissions().all {
        Build.VERSION.SDK_INT < 23 || ctx.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestEnable(act: Activity) {
        try { act.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: Throwable) { }
    }

    fun requestDiscoverable(act: Activity) {
        try { act.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)) } catch (_: Throwable) { }
    }

    class Host(ctx: Context, val session: HostSession) : AutoCloseable {
        @Volatile private var running = true
        private var server: BluetoothServerSocket? = null
        @Volatile var error: String? = null

        init {
            val ad = adapter(ctx)
            Thread({
                try {
                    val ss = ad!!.listenUsingInsecureRfcommWithServiceRecord("StarGuysBattle", UUID_GAME)
                    server = ss
                    while (running) {
                        val s: BluetoothSocket = ss.accept() ?: continue
                        val name = try { s.remoteDevice?.name ?: "bt" } catch (_: Throwable) { "bt" }
                        session.onLink(Link("bt-$name", s.inputStream, s.outputStream) { s.close() })
                    }
                } catch (e: Throwable) { if (running) error = e.message ?: e.toString() }
            }, "bt-accept").apply { isDaemon = true }.start()
        }

        override fun close() { running = false; try { server?.close() } catch (_: Throwable) { } }
    }

    /** Lists paired devices and scans for nearby ones. */
    class Finder(val ctx: Context) : AutoCloseable {
        val hosts = ConcurrentHashMap<String, FoundHost>()
        private val ad = adapter(ctx)
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != BluetoothDevice.ACTION_FOUND) return
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                d?.let { add(it, "à proximité") }
            }
        }
        private var registered = false

        init {
            try { ad?.bondedDevices?.forEach { add(it, "appairé") } } catch (_: Throwable) { }
            scan()
        }

        fun scan() {
            try {
                if (!registered) { ctx.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND)); registered = true }
                ad?.cancelDiscovery(); ad?.startDiscovery()
            } catch (_: Throwable) { }
        }

        private fun add(d: BluetoothDevice, how: String) {
            val addr = try { d.address } catch (_: Throwable) { return }
            val name = try { d.name ?: addr } catch (_: Throwable) { addr }
            if (hosts.containsKey(addr)) { hosts[addr]?.seen = System.currentTimeMillis(); return }
            hosts[addr] = FoundHost(addr, name, "Bluetooth · $how", System.currentTimeMillis()) {
                try { ad?.cancelDiscovery() } catch (_: Throwable) { }
                val s = d.createInsecureRfcommSocketToServiceRecord(UUID_GAME)
                s.connect()
                Link("bt-$name", s.inputStream, s.outputStream) { s.close() }
            }
        }

        override fun close() {
            try { ad?.cancelDiscovery() } catch (_: Throwable) { }
            try { if (registered) ctx.unregisterReceiver(receiver) } catch (_: Throwable) { }
        }
    }
}
