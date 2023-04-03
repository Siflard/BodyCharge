package com.example.bodycharge

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask

class Service_Ble : Service() {


    //lateinit var ordresAEnvoyer: ByteArray

    //BINDER
    // Binder given to clients
    private val binder = LocalBinder()
    var handler: Handler? = null
    val intentEnvoyerSurReceiver = Intent(CommunicationServiceBLE.ACTION)
    var distance = "0"
    var caracteristiqueInfoWrite: BluetoothGattCharacteristic? = null
    var caracteristiqueInfoRead: BluetoothGattCharacteristic? = null


///////////CONNECTION DU SERVICE//////////////////////////

    inner class LocalBinder : Binder() {
        fun getService(): Service_Ble = this@Service_Ble
        override fun toString(): String {
            return ""
        }


    }


    override fun onBind(intent: Intent): IBinder {
        startBleScan()
        return binder
    }
///////////////////////////////////////


    ////////Variable Utiles/////////////////////////

    var connecte = false
    var listeDevice = ArrayList<String>()


    var timerComm = Timer()

    val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    lateinit var capteurDevice: BluetoothDevice
    lateinit var bluetoothGatt: BluetoothGatt

    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(
            ParcelUuid.fromString(CARAC.SERVICE_UUID)
        ).build()

    val scanResults = mutableListOf<ScanResult>()

    private var isScanning = false


///////////////////////////////////////////////////:

    ///////////////////////////////////////////////
    //////////      Fonction SCAN       ///////////
    ////////// Detection des oiseaux    ///////////
    ///////////////////////////////////////////////

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (bluetoothAdapter.isEnabled) {

            val scanFilters: MutableList<ScanFilter> = ArrayList()
            scanFilters.add(scanFilter)
            bleScanner.startScan(
                scanFilters,
                scanSettings,
                scanCallback
            ) // on ne détecte que les Capteurs
            isScanning = true

        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false


    }
////////////////////////////////////////////////////////////


    /////////////RECUPERE LE RESULTAT DU SCAN///////////////////////
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            Log.d("resultat", result.device.address)
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result

            } else {
                with(result.device) {
                    listeDevice.add(result.device.address)
                    Log.d("SERVICE BLE", "Oiseau trouvé : ${result.device.address}")


                }
                scanResults.add(result)

            }
            if (listeDevice.isNotEmpty()) {
                listeDevice.sort()
                intentEnvoyerSurReceiver.putExtra(
                    CommunicationServiceBLE.TYPE_EXTRA,
                    CommunicationServiceBLE.TYPE_EXTRA.SCAN_CALLBACK
                )
                intentEnvoyerSurReceiver.putExtra(
                    CommunicationServiceBLE.LISTE_APPAREIL,
                    listeDevice
                )
                sendBroadcast(intentEnvoyerSurReceiver)
            }
        }


        ///////////////////////////////////////::::

        ////GESTION ETAT SCAN ECHOUE ///////////////////
        override fun onScanFailed(errorCode: Int) {

        }
    }
    private var onCharacteristicInfoWrite: () -> Unit = {}
    fun setOnCharacteristicInfoWrite(f: () -> Unit) {
        onCharacteristicInfoWrite = f
    }

    private var onCharacteristicInfoRead: () -> Unit = {}
    fun setonCharacteristicInfoRead(f: () -> Unit) {
        onCharacteristicInfoRead = f
    }

    //////////////AUTORISE LES NOTIFICATION SUR LA CHARAC CHOISIS //////////////////////////
    @SuppressLint("MissingPermission")
    fun activerNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean): Boolean {
        bluetoothGatt.setCharacteristicNotification(characteristic, true)
        val descriptor: BluetoothGattDescriptor =
            characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return bluetoothGatt.writeDescriptor(descriptor)
    }

    /////////////////////////////INDICATION DE LA CONNECTION / DECONNECTION DU CLIENT (OISEAU) AU SERVICE /////////////////////////

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("gattState", "connect")
                    bluetoothGatt = gatt



                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt.discoverServices()

                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timer("BLE").cancel()
                    scanResults.clear()
                    connecte = false
                    gatt.close()
                    startBleScan()
                    Log.d("gattState", "disconnect")

                }
            } else {
                connecte = false
                Timer("BLE").cancel()
                scanResults.clear()
                gatt.close()
                startBleScan()


            }
        }


        ///////////////OISEAU CONNECTE GESTION DE L'ECRITURE / TIMER ///////////////////////


        @SuppressLint("MissingPermission", "SuspiciousIndentation")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                caracteristiqueInfoWrite =
                    bluetoothGatt.getService(UUID.fromString(CARAC.SERVICE_UUID))
                        .getCharacteristic(UUID.fromString(CARAC.REQUEST_UUID))
                caracteristiqueInfoRead =
                    bluetoothGatt.getService(UUID.fromString(CARAC.SERVICE_UUID))
                        .getCharacteristic(UUID.fromString(CARAC.RESPONSE_UUID))

                connecte = true
                stopBleScan()
                timerComm.scheduleAtFixedRate(timerTask {
                    readRemoteRssi()
                    Log.d("RSSI", distance)
                }, 0, 1000)


            }

        }


        ////////////////////////////////////////////////////////////

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {


        }




        //////////FONCTION DEBUG VERIFICATION ECRITURE////////////////

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (connecte) {
                when (characteristic) {
                    caracteristiqueInfoWrite -> {
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> {
                                onCharacteristicInfoWrite()


                            }
                        }
                    }

                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            distance = rssi.toString()
            intentEnvoyerSurReceiver.putExtra(CommunicationServiceBLE.RSSI, rssi.toString())
            intentEnvoyerSurReceiver.putExtra(
                CommunicationServiceBLE.TYPE_EXTRA,
                CommunicationServiceBLE.TYPE_EXTRA.UPDATE_RSSI
            )
            sendBroadcast(intentEnvoyerSurReceiver)
        }


        ///////////////////////////////////////////////////////////
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (connecte) {
                when (characteristic) {
                    caracteristiqueInfoRead -> {
                        when (status) {
                            BluetoothGatt.GATT_SUCCESS -> {
                                onCharacteristicInfoRead()
                            }
                        }
                    }


                }
            }
        }
    }


    //////////////Verification possibilité de lire la characteristic////////////////////
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    ////////////Verification possibilité d'ecrire la characteristic/////////////////

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse() =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    ///////////////////////////////////////////

    ///////////////Verification proprieté de la charac//////////////////////////

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }
    ////////////////////////////////////


    /////////////FONCTION ECRITURE DES CHARACTERISIC//////////////////////

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {


        if (connecte) {
            val writeType = when {
                characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.isWritableWithoutResponse() -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                else -> error("Characteristic ${characteristic.uuid} cannot be written to")
            }

            bluetoothGatt.let { gatt ->
                characteristic.writeType = writeType
                characteristic.value = payload
                gatt.writeCharacteristic(characteristic)
            }
        }

    }




    //////////////RECUPERATION DU facteur environemental pour l'affichage de la distance ///////////////////

    //////////////CONNECTION A L'oiseau sélectionné dans l'activity ChoixControl ///////////////////

    @SuppressLint("MissingPermission")
    fun ConnectionAppareilSelected(int: Int) {
        if (scanResults.isNotEmpty()) {
            scanResults.sortWith(compareBy { it.device.address })
            capteurDevice = scanResults[int].device
            stopBleScan()
            capteurDevice.connectGatt(applicationContext, true, gattCallback)
        }


        return

    }

    //////////////DECONNECTION de l'oiseau et Relance du BLEscanne Pour la détection de nouveau oiseaux ///////////////////


    //////////////Raffraichissement du recycler view  ///////////////////


    lateinit var trameMeasure : TRAME_TYPE

    @SuppressLint("MissingPermission")
    fun lectureCapteur(gatt: BluetoothGatt) {
        val DemandeInfo = byteArrayOf(TRAME_MEASURE.NOUVELLE_MESURE.toByte())
        setonCharacteristicInfoRead {
            trameMeasure = TRAME_TYPE(
                //HR
                caracteristiqueInfoRead!!.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT16,
                    0
                ),
                //TEMPERATURE
                caracteristiqueInfoRead!!.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT16,
                    2
                ),

            )
            Log.d("Trame", "$trameMeasure")
            intentEnvoyerSurReceiver.putExtra(CommunicationServiceBLE.TYPE_EXTRA,CommunicationServiceBLE.TYPE_EXTRA.TRAME_CAPTEUR)
            intentEnvoyerSurReceiver.putExtra(CommunicationServiceBLE.LECTURE_CAPTEUR,trameMeasure.toString())
            sendBroadcast(intentEnvoyerSurReceiver)
            setonCharacteristicInfoRead {}
        }
        setOnCharacteristicInfoWrite {
            gatt.readCharacteristic(caracteristiqueInfoRead)
            setOnCharacteristicInfoWrite {}
        }
        writeCharacteristic(caracteristiqueInfoWrite!!, DemandeInfo)


    }

    fun ordreLed(color : Int){
        writeCharacteristic(caracteristiqueInfoWrite!!, byteArrayOf(color.toByte()))
    }


}


