package com.ygwk.project_2

class GPSData {
    var gpslati: Double = 0.0
    var gpslong: Double = 0.0

    constructor(lati: Double, long2: Double) {
        gpslati = lati
        gpslong = long2
    }
}

class StationClass {
    var name: String = ""
    lateinit var latLng: GPSData

    constructor(name1: String, lat: GPSData) {
        name = name1
        latLng = lat
    }
}