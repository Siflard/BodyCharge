package com.example.bodycharge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.internal.ContextUtils
import com.google.android.material.internal.ContextUtils.getActivity
import java.lang.reflect.Type
import java.util.*
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity(), RecyclerView_Detection.OnItemClickListener {
    private var mBoundTimer: Boolean = false
    private lateinit var timerService: TimerService
    private var mBound: Boolean = false
    private var mServiceBLE: Service_Ble? = null
    lateinit var receiver: BroadcastReceiver
    private var refusActiverBluetooth = false
    var timerComm = Timer()
    var deviceIp = ArrayList<String>()
    var deviceRssi = ""

    private val connectionTimer = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            mBoundTimer = true
            timerService.startService()
            timerService.setTiming(1000)
            timerService.fonctionTime({Log.d("fonctionne", "fonctionne")})
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBoundTimer = false
        }
    }


    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as Service_Ble.LocalBinder
            mServiceBLE = binder.getService()
            mBound = true

        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }


    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private val activityResultLauncher =
        /********************************
         * Demande l'activation des permissions listées
         * Ouvre les paramètres pour permissions manuelles si don't ask again activé
         * Lance un scan si toutes les permissions nécessaires sont données
         * pour lancer l'activité : activityResultLauncher.launch(listePermissions.toTypedArray())
         ********************************/
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        /*
        Le RequestMultiplePermissions est une fonction fournie dans la classe ActivityResultContracts
        qui regroupe un ensemble de fonctions usuelles. Elles incluent notament la gestion du Intent
         */

        { permissions ->
            // Handle Permission granted/rejected
            var i = 0
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                i++
                if (isGranted) {
                    // Permission is granted
                } else {
                    //on teste si c'est bien la dernière premission refusée dans la liste, sinon l'alertDialog va s'ouvrir autant de fois qu'il y a de permissions refusées
                    if (i >= permissions.size && !shouldShowRequestPermissionRationale(
                            permissionName
                        )
                    )//plus d'un refus = don't ask again, la permission ne sera pas demandée
                    {
                        ouvrirParametresApp()

                    }
                    // Permission is denied
                }
            }

            if (permissionsNecessairesAutorisees()) {
                promptEnableBluetooth()
                Intent(
                    this@MainActivity,
                    Service_Ble::class.java
                ).also { intent ->
                    bindService(intent, connection, BIND_AUTO_CREATE)

                }
                Intent(this@MainActivity, TimerService::class.java
                ).also { intent -> bindService(intent, connectionTimer, BIND_AUTO_CREATE)

                }

            }
        }

    ///////Verification Permission////////

    private fun permissionsNecessairesAutorisees(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN)&&hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)&&hasPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE)&&hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        else {//cas API<30
            return hasPermission(Manifest.permission.BLUETOOTH) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)&&hasPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE)&&hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }


//////////////////


    /////////Demande les permissions uniquement si elles ne sont pas acceptées/////////////////


    @RequiresApi(Build.VERSION_CODES.M)
    private fun demanderPermissionsManquantes() {
        var listePermissions = mutableListOf<String>()

        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listePermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
                listePermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (!hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            listePermissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            listePermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT <= 30) {
            listePermissions.add(android.Manifest.permission.BLUETOOTH)
            listePermissions.add(android.Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT <= 29) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listePermissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            listePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            listePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (listePermissions.isNotEmpty()) {
            val image = ImageView(this)
            val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("À propos des autorisations")
                .setView(image)
                .setMessage(
                    "Pour permettre le fonctionnement de l'application, nous avons besoin que vous autorisiez 2 permissions Android :\n\r" +
                            "- les objets à proximité, pour permettre d'activer le Bluetooth\n" +
                            "- ET la localisation (qui ne permet que de détecter les appareil).\n\n" +
                            "Nous n'utilisons pas cette autorisation pour connaître votre position.\n" +
                            "En cas de refus, l'application ne pourra pas fonctionner."
                )
                .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                    activityResultLauncher.launch(listePermissions.toTypedArray())
                }
                .setCancelable(false)//pour éviter de fermer la fenêtre et donc de ne pas déclencher la demande de permissions en appuyant sur le retour
                .show()
        }
    }

    ////////////////////Ouvre les parametres de l'applications si trop de refus de permission ////////////////////

    private fun ouvrirParametresApp() {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        lateinit var mAlertDialog: AlertDialog
        alertDialogBuilder.setTitle("À propos des autorisations")
            .setMessage(
                "Vous avez refusé 2 fois la permission nécessaire au fontionnement de l'application.\n" +
                        "Autorisez les permissions requises dans le menu paramètres ou réinstallez l'application."
            )
            .setPositiveButton("Ouvrir les paramètres") { _: DialogInterface, _: Int ->
                mAlertDialog.cancel()
                //Permet d'ouvrir les paramètres de l'app pour ajouter les permissions manuellement
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri: Uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Fermer", null)

        mAlertDialog = alertDialogBuilder.create()
        mAlertDialog.show()
    }
/////////////////////


    ////////////Verification de l'activation du BT ////////////////////////
    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun promptEnableBluetooth() {
        if (permissionsNecessairesAutorisees() && !bluetoothAdapter.isEnabled) {
            activerBT.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private val activerBT = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            mServiceBLE!!.startBleScan()
        } else {
            //L'utilisateur n'a pas accepté d'activer le Bluetooth
            if (!refusActiverBluetooth)//c'est le premier refus (évite de boucler ici)
            {
                refusActiverBluetooth = true
                messageDemanderActivationBT()//fenetre dialog
            }
        }
    }


    private fun messageDemanderActivationBT() {
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        lateinit var mAlertDialog: AlertDialog
        alertDialogBuilder.setTitle("Activation Bluetooth")
            .setMessage(
                "L'activation du Bluetooth est nécessaire"
            )
            .setPositiveButton("Activer le Bluetooth") { _: DialogInterface, _: Int ->
                mAlertDialog.cancel()
                activerBT.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            .setNegativeButton("Fermer", null)

        mAlertDialog = alertDialogBuilder.create()
        mAlertDialog.show()
    }


    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        /////////////:Verification permissions Lancement du Service/////////////
        if (permissionsNecessairesAutorisees()) {
            Intent(
                this@MainActivity,
                Service_Ble::class.java
            ).also { intent ->
                bindService(intent, connection, BIND_AUTO_CREATE)
            }
            Intent(this@MainActivity, TimerService::class.java
            ).also { intent -> bindService(intent, connectionTimer, BIND_AUTO_CREATE)

            }


            promptEnableBluetooth()

        } else {
            demanderPermissionsManquantes()

            /*Intent(this@MainActivity, ServiceBLE::class.java).also { intent_receiver ->
                bindService(intent_receiver, connection, BIND_AUTO_CREATE)
            }*/
        }
        val buttonScan = findViewById<Button>(R.id.buttonConnect)

        val recyclerViewDetection = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerViewDetection.layoutManager = LinearLayoutManager(this)


        val filter = IntentFilter(CommunicationServiceBLE.ACTION)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    CommunicationServiceBLE.ACTION -> {
                        when (intent.getStringExtra(CommunicationServiceBLE.TYPE_EXTRA)) {
                            CommunicationServiceBLE.TYPE_EXTRA.SCAN_CALLBACK -> {
                                if (intent.getStringArrayListExtra(CommunicationServiceBLE.LISTE_APPAREIL) != null) {

                                    deviceIp = intent.getStringArrayListExtra(CommunicationServiceBLE.LISTE_APPAREIL)!!
                                    deviceIp.sort()
                                    Log.d("resultatd", "${deviceIp}")

                                }
                            }
                            CommunicationServiceBLE.TYPE_EXTRA.UPDATE_RSSI ->{
                                if (intent.getStringExtra(CommunicationServiceBLE.RSSI)!=null){
                                    deviceRssi = intent.getStringExtra(CommunicationServiceBLE.RSSI)!!
                                }
                            }
                        }
                    }
                }
            }

        }

        registerReceiver(receiver, filter)


        buttonScan.setOnClickListener{
            mServiceBLE!!.startBleScan()
            timerService.setTiming(1000)
            timerService.fonctionTime {
                getActivity(this@MainActivity)?.runOnUiThread {
                    Log.d("resultatd", "${deviceIp}")
                    val adapterRecyclerviewDetection = RecyclerView_Detection(CreerMesLigne(), this@MainActivity)
                    recyclerViewDetection.adapter = adapterRecyclerviewDetection

                }
            }
            /*val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
            this.finish()*/
        }

    }


    override fun onStop() {//écran s'éteint; on gère pour éviter l'envoie de commandes à l'model_oiseau sans voir ce qu'on fait
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onItemClick(position: Int) {
        Toast.makeText(this, "${mServiceBLE!!.connecte}", Toast.LENGTH_SHORT).show()
        mServiceBLE!!.ConnectionAppareilSelected(position)
        val intent = Intent(this@MainActivity, CommandeetLecture::class.java)
        startActivity(intent)
        this.finish()
    }

    fun CreerMesLigne(): ArrayList<TypeAppareil> {
        val appareil = ArrayList<TypeAppareil>()
        for (i in deviceIp) {

            val ip = getSharedPreferences("Bird", Context.MODE_PRIVATE)?.getString(i, "abc")
            if (ip != "abc") {
                appareil.add(TypeAppareil(ip!!, ""))
            } else {
                appareil.add(TypeAppareil(i, deviceRssi))
            }
        }
        return appareil
    }
}