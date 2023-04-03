package com.example.bodycharge


data class TypeAppareil (val ip : String, val rssi : String)


class CommunicationServiceBLE {
    companion object {
        const val ACTION = "com.example.smileapp.ACTION_SERVICE_BLE"
        const val TYPE_EXTRA = "TYPE_EXTRA"
        const val LISTE_APPAREIL = "LISTE_APPAREIL"
        const val BATTERIE = "BATTERIE"
        const val RSSI = "RSSI"
        const val LECTURE_CAPTEUR = "LECTURE_CAPTEUR"
        const val LOCATIONVALUE = "LOCATIONVALUE"

    }
    class TYPE_EXTRA {
        companion object
        {
            const val SCAN_CALLBACK = "SCAN_CALLBACK"
            const val UPDATE_BATTERIE = "UPDATE_BATTERIE"
            const val UPDATE_RSSI = "UPDATE_RSSI"
            const val CHANGEMENT_ACTIVITE = "CHANGEMENT_ACTIVITE"
            const val TRAME_CAPTEUR = "TRAME_CAPTEUR"
            const val LOCATION = "LOCATION"

        }
    }

}
class TRAME_MEASURE{
    companion object
    {
        const val NOUVELLE_MESURE = 0x00//A changer
        const val ORDRE_LED = 0x00//A changer
    }
}
class CARAC{
    companion object
    {
        const val SERVICE_UUID = "0"//A changer
        const val REQUEST_UUID = "0"//A changer
        const val RESPONSE_UUID = "0"//A changer
    }
}


data class TRAME_TYPE(val nbPas : Int, val battery : Int)

