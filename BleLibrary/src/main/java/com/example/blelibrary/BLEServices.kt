package com.example.blelibrary

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.ContentValues
import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import kotlin.properties.Delegates


private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

// activate sensor uuids
private val char_write = UUID.fromString("a995268a-c467-11e7-abc4-cec278b6b50a")
private val char_write1 = UUID.fromString("a9952cac-c467-11e7-abc4-cec278b6b50a")

// read hr values uuid
private val bpm_hr = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val bpm_not = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val bpm_descriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private val bpm_pos = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")


/**
 * B l e services
 *
 * @constructor Create empty B l e services
 */
class BLEServices {

    // declare variable for match regex pattern
    private val re = "[^A-Za-z0-9 _-]".toRegex()

    // declare variable to store service information (e.g. name)
     private var servicesNames = mutableMapOf<String,String>()

    // declare list to append ble devices founded
    private var scanResults = mutableListOf<ScanResult>()

    // declare variable to pass context from activity
    private var context: Context? = null

    //declare variable for bluetooth gatt connection
    private var bluetoothGatt: BluetoothGatt? = null

    // Stops scanning after 5 seconds.
    private var scanTime: Long = 5000

    // declare variable
    private var isScanning = false

    // declare variable to append measurements from ble devices
    var mapWithValues = mutableMapOf<String, String>()

    // declare variable to count the measurement time
    private var counter by Delegates.notNull<Int>()

    // declare variable for apply filter during the scan for devices
    private lateinit var scanFilter: MutableList<String>

    // declare variable to store the settings to scan for devices
    private lateinit var scanSettings: ScanSettings

    // Callback for scanning devices
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
                val indexQuery =
                    scanResults.indexOfFirst { it.device.address == result.device.address }
                if (indexQuery != -1) { // A scan result already exists with the same address
                    scanResults[indexQuery] = result
                } else {
                    with(result.device) {
                        Log.e("Found BLE device! Name", "${name ?: "Unnamed"}, address: $address")
                    }
                    scanResults.add(result)
                }
            }
    }

    // gatt callback
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(
                        "BluetoothGattCallback",
                        "Successfully connected to ${gatt.device.address}"
                    )

                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Disconnected from the GATT server
                    Log.v(ContentValues.TAG, "[INFO] Disconnected from the GATT server")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered ${services.size} services for ${device.address}"
                )
                printGattTable() // See implementation just above this section
                readBatteryLevel()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "BluetoothGattCallback",
                            "Read characteristic $uuid:\n${value.toHexString()} == ${
                                value.first().toInt()
                            }%"
                        )
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic read failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Log.i(
                    "BluetoothGattCallback",
                    "Characteristic $uuid changed | value: ${value.toHexString()}}" +
                            "\n${value[0]}\n" +
                            "${value[1]}\n" +
                            "${value[2]}\n" +
                            "${value[3]}"
                )
                val a = value.toHexString()
                println(counter)
                mapWithValues[counter.toString()] = a
//                println(mapWithoutValues)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "BluetoothGattCallback",
                            "Wrote to characteristic $uuid | value: ${value.toHexString()}"
                        )
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic write failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }
    }

//    private fun getServicesNames(){
//
//        val servicesFile = context?.resources?.openRawResource(R.res.raw.uuid_services)
//        val contentFile = servicesFile?.bufferedReader()
//        val contents = contentFile?.readLines()
//
//        for (line in contents!!) {
//            val content = re.replace(line, "").split(" ")
//
//            if (content.size>2) {
//                servicesNames[content[0]]  = content.slice(1 until content.size).joinToString(" ")
//            }
//            else {
//                servicesNames[content[0]] = content[1]
//            }
//        }
//    }


    /**
     * Print gatt table
     *
     */
    fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                "printGattTable",
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) {
                it.uuid.toString()
            }
            Log.i(
                "printGattTable",
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }


    /**
     * Write characteristic
     *
     * @param characteristic
     * @param payload
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }


    /**
     * Read battery level
     *
     */
    @SuppressLint("MissingPermission")
    fun readBatteryLevel() {
        val batteryLevelChar = bluetoothGatt
            ?.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
        Log.w("PropertyTYPE", "Is readable  ${batteryLevelChar?.isReadable()}")

        if (batteryLevelChar?.isReadable() == true) {
            bluetoothGatt?.readCharacteristic(batteryLevelChar)
        }
    }

    /**
     * Write descriptor
     *
     * @param descriptor
     * @param payload
     */
    @SuppressLint("MissingPermission")
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }


    /**
     * Enable notifications
     *
     * @param characteristic
     */
    @SuppressLint("MissingPermission")
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = bpm_descriptor
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    /**
     * Count up timer
     *
     * @property secondsInFuture
     * @constructor
     *
     * @param countUpIntervalSeconds
     */
    abstract class CountUpTimer(private val secondsInFuture: Int, countUpIntervalSeconds: Int) :
        CountDownTimer(secondsInFuture.toLong() * 1000, countUpIntervalSeconds.toLong() * 1000) {

        /**
         * On count
         *
         * @param count
         */
        abstract fun onCount(count: Int)

        override fun onTick(msUntilFinished: Long) {
            onCount(((secondsInFuture.toLong() * 1000 - msUntilFinished) / 1000).toInt())
        }
    }

    private fun minToSeconds(minutes: Double): Int {
        return (minutes * 60.0).toInt()
    }

    /**
     * Activate notification
     *
     * @param Service_UUID
     * @param Characteristic_UUID
     */
    fun activateNotification(Service_UUID: String, Characteristic_UUID: String) {

        val serviceUUID = UUID.fromString(Service_UUID)
        val characteristicUUID = UUID.fromString(Characteristic_UUID)

        val read_bpm = bluetoothGatt
            ?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
        read_bpm?.let { enableNotifications(it) }

        val aquisition_time = minToSeconds(3.0)

        object : CountUpTimer(aquisition_time, 1) {
            override fun onCount(count: Int) {
                Log.i("Counter", "Counting: $count")
                counter = count
            }

            @SuppressLint("MissingPermission")
            override fun onFinish() {
                Log.i("Counter", "Counting done")
                println(mapWithValues)
                val characteristic = bluetoothGatt?.getService(char_write)
                    ?.getCharacteristic(char_write1)

                val value = byteArrayOf(0x02, 0xAB.toByte())
                println(value)
                characteristic?.let { writeCharacteristic(it, value) }
//                sendMessage(mapWithoutValues)
                bluetoothGatt?.close()
            }
        }.start()
    }

    /**
     * Is readable
     *
     * @return
     */
    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    /**
     * Is writable
     *
     * @return
     */
    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    /**
     * Is writable without response
     *
     * @return
     */
    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    /**
     * Is notifiable
     *
     * @return
     */
    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    /**
     * Is indicatable
     *
     * @return
     */
    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }


    /**
     * Get results from scan
     *
     * @return
     */
    fun getResultsFromScan(): MutableList<ScanResult> {
        if (scanResults.isEmpty()) {
            Log.e("Scanned Devices", "You didn't search for devices or doesn't have found devices")
        }
        else Log.i("Scanned Devices", "Found devices: ${scanResults.size}")

        return scanResults
    }


    /**
     * Start ble scan
     *
     * @param context
     * @param Ble_Scanner
     * @param Scan_Settings
     * @param Scan_Filter
     * @param Scan_Period
     */
    @SuppressLint("MissingPermission")
    fun startBleScan(context: Context,
                     Ble_Scanner: BluetoothLeScanner,
                     Scan_Settings: ScanSettings?,
                     Scan_Filter: MutableList<String>?,
                     Scan_Period: Long?
    ) {
        this.context = context

//        getServicesNames()

        if (Scan_Period != null && Scan_Period != 0L) {
            scanTime = Scan_Period
        }

        if (Scan_Settings != null) {
            scanSettings = Scan_Settings
        }
        else (getScanSettings())

        scanFilter = Scan_Filter ?: mutableListOf("MindProber_HR", "MindProber_GSR")
        if (!isScanning) {

            // Stops scanning after a pre-defined scan period.
            Handler(Looper.getMainLooper()).postDelayed({ isScanning = false
                Ble_Scanner.stopScan(leScanCallback) }, scanTime)

            isScanning = true
            try {
                Ble_Scanner.startScan(scanFilters(scanFilter), scanSettings, leScanCallback)
                Log.i("Bluetooth Scanner","Bluetooth Scan Started")
            } catch (e: Throwable) {
                Log.e("Bluetooth Scanner","Error bluetoothScanner.startScan() $e")
            }
        }
    }

    /**
     * Connect gatt service
     *
     * @param context
     * @param device
     */
    @SuppressLint("MissingPermission")
    fun connectGattService(context: Context, device: BluetoothDevice) {
        Log.i("Connect Gatt Services", "Connecting to gatt services")
        bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback)
    }


    /**
     * Disconnect gatt service
     *
     */
    @SuppressLint("MissingPermission")
    fun disconnectGattService() {
        bluetoothGatt?.close()
        Log.i ("Gatt Services", "Sucessefully disconnected")
    }


    companion object {
        /** ----------------------------  BLUETOOTH & FILTERS SETTINGS  ----------------------------- */

        // only defined scan mode
        private fun BLEServices.getScanSettings(): ScanSettings {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build().also { scanSettings = it }
            return scanSettings
        }

        // apply filters
        private fun scanFilters(DeviceNames: MutableList<String>): List<ScanFilter> {
            val list: MutableList<ScanFilter> = ArrayList()

            for (deviceName in DeviceNames) {
                list.add(
                    ScanFilter.Builder()
                        .setDeviceName(deviceName).build()
                )
            }
            return list
        }
    }
}
