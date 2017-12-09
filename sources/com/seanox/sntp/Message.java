/**
 *  LIZENZBEDINGUNGEN - Seanox Software Solutions ist ein Open-Source-Projekt,
 *  im Folgenden Seanox Software Solutions oder kurz Seanox genannt.
 *  Diese Software unterliegt der Version 2 der GNU General Public License.
 *
 *  Time, Daytime and SNTP extension for Seanox Devwex
 *  Copyright (C) 2017 Seanox Software Solutions
 *
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms of version 2 of the GNU General Public License as published
 *  by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package com.seanox.sntp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;

/**
 *  Message stellt ein Objekt zur Haltung und zur Konvertierung sowie
 *  Verarbeitung von NTP-Daten zur Verf&uuml;gung.<br>
 *      <dir>Hinweis</dir>
 *  Diese Klasse basiert auf Quellen der Dateien: {@code NtpMessage.java},
 *  {@code SNTPClient.java} und {@code SNTPServer.java} von <i>Adam Buckley</i>
 *  aus dem <i>OpenNMS-Projekt</i> sowie <i>Thomas Johansson</i> aus dem
 *  <i>Nav2000-Projekt</i>.<br>
 *  <br>
 *  Message 3.0 20171209<br>
 *  Copyright (C) 2017 Seanox Software Solutions<br>
 *  Alle Rechte vorbehalten.
 *
 *  @author  Seanox Software Solutions
 *  @version 3.0 20171209
 */
class Message {

    /** Message Feld Reference Identifier  */
    private byte[] referenceIdentifier;

    /** Message Feld Root Delay */
    private double rootDelay;

    /** Message Feld Root Dispersion */
    private double rootDispersion;

    /** Message Feld Leap Indicator */
    private int leapIndicator;

    /** Message Feld Mode */
    private int mode;

    /** Message Feld Poll Interval */
    private int pollInterval;

    /** Message Feld Precision */
    private int precision;

    /** Message Feld Stratum */
    private int stratum;

    /** Message Feld Version */
    private int version;

    /** Message Feld Originate Timestamp */
    private long originateTimestamp;

    /** Message Feld Receive Timestamp */
    private long receiveTimestamp;

    /** Message Feld Reference Timestamp */
    private long referenceTimestamp;

    /** Message Feld Transmit Timestamp */
    private long transmitTimestamp;

    /** Basiszeit beim Wert 0 beim Bit-0 (7.2.2036 06:28:16) */
    private static final long NTP_BASE_TIME_0 = 2085978496000L;

    /** Basiszeit beim Wert 1 beim Bit-0 (1.1.1900 01:00:00) */
    private static final long NTP_BASE_TIME_1 = -2208988800000L;

    /** Konstruktor, richtet das Message ein. */
    Message() {
        this.referenceIdentifier = new byte[] {0, 0, 0, 0};
    }

    /**
     *  Tr&auml;gt im &uuml;bergebenen ByteArray ab dem Offset den angegebenen
     *  int-Wert als 32-Bit Integer ein.
     *  @param  bytes  ByteArray
     *  @param  offset Position an der der int-Wert gesetzt wird
     *  @param  value  int-Wert
     */
    private static void encodeInt(byte[] bytes, int offset, int value) {

        int loop;
        
        for (loop = 4 -1; loop >= 0; loop--) {
            bytes[offset +loop] = (byte)(value & 0xFF);
            value = value >> 8;
        }
    }

    /**
     *  Tr&auml;gt im &uuml;bergebenen ByteArray ab dem Offset den angegebenen
     *  long-Wert als 64-Bit Integer ein.
     *  @param  bytes  ByteArray
     *  @param  offset Position an der der long-Wert gesetzt wird
     *  @param  value  long-Wert
     */
    private static void encodeLong(byte[] bytes, int offset, long value) {

        int loop;
        
        for (loop = 8 -1; loop >= 0; loop--) {
            bytes[offset +loop] = (byte)(value & 0xFF);
            value = value >> 8;
        }
    }

    /**
     *  Ermittelt einen int-Wert ab dem Offset aus dem angebenen ByteArray.
     *  @param  bytes  ByteArray
     *  @param  offset Position von der aus der int-Wert ermittelt wird
     *  @return der ermittelte int-Wert
     */
    private static int decodeInt(byte[] bytes, int offset) {

        int loop;
        int result;

        for (loop = offset, result = 0; loop < offset +4; loop++) {
            result = result << 8;
            result = result | (bytes[loop] & 0xFF);
        }

        return result;
    }

    /**
     *  Ermittelt einen long-Wert ab dem Offset aus dem angebenen ByteArray.
     *  @param  bytes  ByteArray
     *  @param  offset Position von der aus der long-Wert ermittelt wird
     *  @return der ermittelte long-Wert
     */
    private static long decodeLong(byte[] bytes, int offset) {

        int  loop;
        long result;

        for (loop = offset, result = 0; loop < offset +8; loop++) {
            result = result << 8;
            result = result | ((long)bytes[loop] & 0xFF);
        }

        return result;
    }

    /**
     *  Konvertiert den unsigned Byte in short.
     *  @param  digit unsigned Byte
     *  @return der ermittelte short-Wert
     */
    private static short unsignedByteToShort(byte digit) {
        return (short)(((digit & 0x80) == 0x80) ? (128 +(digit & 0x7F)) : digit);
    }

    /**
     *  Konvertiert die angegebenen Millisekunden in einen 64-Bit NTP Timestamp.
     *  @param  milliseconds Millisekunden
     *  @return der erstellte 64-Bit NTP Timestamp
     */
    private static long toNtpTime(long milliseconds) {

        boolean foretime;

        long    basetime;
        long    fraction;
        long    seconds;

        //Status der Zeitrechnung (vor/nach 02/2036)
        foretime = milliseconds < Message.NTP_BASE_TIME_0;

        //die Basiszeit wird entsprechend der Zeitrechnung ermittelt
        basetime = milliseconds -(foretime ? Message.NTP_BASE_TIME_1 : Message.NTP_BASE_TIME_0);
        seconds  = basetime /1000;
        fraction = ((basetime %1000) *0x100000000L) /1000;

        //fuer die Zeitrechung ab 1900 wird das High-Order Bit gesetzt
        if (foretime)
            seconds |= 0x80000000L;

        return seconds << 32 | fraction;
    }

    /**
     *  Konvertiert einen 64-Bit NTP-Timestamp in Millisekunden.
     *  @param  timestamp 64-Bit NTP-Timestamp
     *  @return die ermittelten Millisekunden seit dem 01.01.1970 00:00:00
     */
    private static long convertNtpTime(long timestamp) {

        long fraction;
        long seconds;

        seconds  = (timestamp >>> 32) & 0xFFFFFFFFL;
        fraction = timestamp & 0XFFFFFFFFL;
        fraction = Math.round(1000D *fraction /0x100000000L);

        //mit der Existenz vom Most Significant Bit (MSB) erfolgt die
        //Zeitrechnung ab 01/1900 sonst ab 02/2036
        return (((seconds & 0x80000000L) == 0) ? Message.NTP_BASE_TIME_0 : Message.NTP_BASE_TIME_1) +(seconds *1000) +fraction;
    }

    /**
     *  Erstellt eine NTP-Message auf Basis der NTP-Daten als ByteArray.
     *  @param  bytes NTP-Daten als ByteArray
     *  @return die erstellte NTP-Message
     */
    static Message parseNtpPacket(byte[] bytes) {

        Message message;

        if (bytes == null
                || bytes.length < 48)
            throw new IllegalArgumentException("Invalid NTP packet");

        message = new Message();

        message.leapIndicator = (byte)((bytes[0] >> 6) & 0x3);
        message.version       = (byte)((bytes[0] >> 3) & 0x7);
        message.mode          = (byte)(bytes[0] & 0x7);
        message.stratum       = Message.unsignedByteToShort(bytes[1]);
        message.pollInterval  = bytes[2];
        message.precision     = bytes[3];

        message.rootDelay  = bytes[4] *256D;
        message.rootDelay += Message.unsignedByteToShort(bytes[5]);
        message.rootDelay += Message.unsignedByteToShort(bytes[6]) /256D;
        message.rootDelay += Message.unsignedByteToShort(bytes[7]) /65536D;

        message.rootDispersion  = Message.unsignedByteToShort(bytes[8]) *256D;
        message.rootDispersion += Message.unsignedByteToShort(bytes[9]);
        message.rootDispersion += Message.unsignedByteToShort(bytes[10]) /256D;
        message.rootDispersion += Message.unsignedByteToShort(bytes[11]) /65536D;

        message.referenceIdentifier = Arrays.copyOfRange(bytes, 12, 12 +4);

        message.referenceTimestamp = Message.convertNtpTime(Message.decodeLong(bytes, 16));
        message.originateTimestamp = Message.convertNtpTime(Message.decodeLong(bytes, 24));
        message.receiveTimestamp   = Message.convertNtpTime(Message.decodeLong(bytes, 32));
        message.transmitTimestamp  = Message.convertNtpTime(Message.decodeLong(bytes, 40));

        return message;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds rootDelay.
     *  @return der Wert vom Feld rootDelay
     */
    double getRootDelay() {
        return this.rootDelay;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld rootDelay.
     *  @param rootDelay Wert rootDelay
     */
    void setRootDelay(double rootDelay) {
        this.rootDelay = rootDelay;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds rootDispersion.
     *  @return der Wert vom Feld rootDispersion
     */
    double getRootDispersion() {
        return this.rootDispersion;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld rootDispersion.
     *  @param rootDispersion Wert rootDispersion
     */
    void setRootDispersion(double rootDispersion) {
        this.rootDispersion = rootDispersion;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds leapIndicator.
     *  @return der Wert vom Feld leapIndicator
     */
    int getLeapIndicator() {
        return this.leapIndicator;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld leapIndicator.
     *  @param leapIndicator Wert leapIndicator
     */
    void setLeapIndicator(int leapIndicator) {
        this.leapIndicator = leapIndicator;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds mode.
     *  @return der Wert vom Feld mode
     */
    int getMode() {
        return this.mode;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld mode.
     *  @param mode Wert mode
     */
    void setMode(int mode) {
        this.mode = mode;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds pollInterval.
     *  @return der Wert vom Feld pollInterval
     */
    int getPollInterval() {
        return this.pollInterval;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld pollInterval.
     *  @param pollInterval Wert pollInterval
     */
    void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds precision.
     *  @return der Wert vom Feld precision
     */
    int getPrecision() {
        return this.precision;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld precision.
     *  @param precision Wert precision
     */
    void setPrecision(int precision) {
        this.precision = precision;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds stratum.
     *  @return der Wert vom Feld stratum
     */
    int getStratum() {
        return this.stratum;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld stratum.
     *  @param stratum Wert stratum
     */
    void setStratum(int stratum) {
        this.stratum = stratum;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds version.
     *  @return der Wert vom Feld version
     */
    int getVersion() {
        return this.version;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld version.
     *  @param version Wert version
     */
    void setVersion(int version) {
        this.version = version;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds originateTimestamp.
     *  @return der Wert vom Feld originateTimestamp
     */
    long getOriginateTimestamp() {
        return this.originateTimestamp;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld originateTimestamp.
     *  @param originateTimestamp Wert originateTimestamp
     */
    void setOriginateTimestamp(long originateTimestamp) {
        this.originateTimestamp = originateTimestamp;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds receiveTimestamp.
     *  @return der Wert vom Feld receiveTimestamp
     */
    long getReceiveTimestamp() {
        return this.receiveTimestamp;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld receiveTimestamp.
     *  @param receiveTimestamp Wert receiveTimestamp
     */
    void setReceiveTimestamp(long receiveTimestamp) {
        this.receiveTimestamp = receiveTimestamp;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds referenceIdentifier.
     *  @return der Wert vom Feld referenceIdentifier
     */
    String getReferenceIdentifier() {

        String string;

        int    loop;
        short  value;

        //der Reference Identifier wird nach RFC 2030 ermittelt
        if (this.stratum == 0
                || this.stratum == 1) {

            //bei Stratum 0/1 wird der Identifier als 4 Zeichen ASCII verwendet
            return new String(this.referenceIdentifier).trim();

        } else if (this.version == 3) {

            //bei NTP-Version 3 wird der Identifier als 32-bit IPv4 Adresse verwendet
            for (loop = 0, string = ""; loop < 4; loop++) {
                if (loop > 0)
                    string = string.concat(".");
                value = Message.unsignedByteToShort(this.referenceIdentifier[loop]);
                string = string.concat(String.valueOf(value));
            }
            return string;

        } else if (this.version == 4) {

            //bei NTP-Version 4 wird der Identifier als 32-Bit verwendet
            for (loop = 0, string = ""; loop < 4; loop++) {
                value = (short)(Message.unsignedByteToShort(this.referenceIdentifier[loop]) /(256L << (8 *loop)));
                string = string.concat(String.valueOf(value));
            }
            return string;
        }

        return "";
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld referenceIdentifier.
     *  @param referenceIdentifier Wert referenceIdentifier
     */
    void setReferenceIdentifier(String referenceIdentifier) {

        if (referenceIdentifier == null)
            referenceIdentifier = "";
        referenceIdentifier.trim().toUpperCase();

        if (referenceIdentifier.length() > 4)
            referenceIdentifier = referenceIdentifier.substring(0, 4);

        while (referenceIdentifier.length() < 4)
            referenceIdentifier = referenceIdentifier.concat("\0");

        this.referenceIdentifier = referenceIdentifier.getBytes();
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds referenceTimestamp.
     *  @return der Wert vom Feld referenceTimestamp
     */
    long getReferenceTimestamp() {
        return this.referenceTimestamp;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld referenceTimestamp.
     *  @param referenceTimestamp Wert referenceTimestamp
     */
    void setReferenceTimestamp(long referenceTimestamp) {
        this.referenceTimestamp = referenceTimestamp;
    }

    /**
     *  R&uuml;ckgabe vom Wert des Felds transmitTimestamp.
     *  @return der Wert vom Feld transmitTimestamp
     */
    long getTransmitTimestamp() {
        return this.transmitTimestamp;
    }

    /**
     *  Setzt den Wert f&uuml;r das Feld transmitTimestamp.
     *  @param transmitTimestamp Wert transmitTimestamp
     */
    void setTransmitTimestamp(long transmitTimestamp) {
        this.transmitTimestamp = transmitTimestamp;
    }

    /**
     *  R&uuml;ckgabe der NTP-Datenstruktur als ByteArray.
     *  @return die NTP-Datenstruktur als ByteArray
     */
    byte[] toNtpPacket() {

        byte[] packet;

        long   value;

        //das ByteArray wird eingerichtet
        packet = new byte[48];

        packet[0] = (byte)(this.leapIndicator << 6 | this.version << 3 | this.mode);

        packet[1] = (byte)this.stratum;
        packet[2] = (byte)this.pollInterval;
        packet[3] = (byte)this.precision;

        //RootDelay ist ein unsigned 16.16-Bit FP, in Java gibt es keinen
        //entsprechenden primitiven Datentyp, so wird ein 32-Bit int verwendet
        value = (int)(this.rootDelay *65536);

        packet[4] = (byte) ((value >> 24) & 0xFF);
        packet[5] = (byte) ((value >> 16) & 0xFF);
        packet[6] = (byte) ((value >>  8) & 0xFF);
        packet[7] = (byte) (value & 0xFF);

        //RootDispersion ist ein unsigned 16.16-Bit FP, in Java gibt es keinen
        //entsprechenden primitiven Datentyp, so wird ein 64-Bit long verwendet
        value = (long)(this.rootDispersion *65536);

        packet[8]  = (byte)((value >> 24) & 0xFF);
        packet[9]  = (byte)((value >> 16) & 0xFF);
        packet[10] = (byte)((value >>  8) & 0xFF);
        packet[11] = (byte)(value & 0xFF);

        Message.encodeInt(packet, 12, Message.decodeInt(this.referenceIdentifier, 0));

        Message.encodeLong(packet, 16, Message.toNtpTime(this.referenceTimestamp));
        Message.encodeLong(packet, 24, Message.toNtpTime(this.originateTimestamp));
        Message.encodeLong(packet, 32, Message.toNtpTime(this.receiveTimestamp));
        Message.encodeLong(packet, 40, Message.toNtpTime(this.transmitTimestamp));

        return packet;
    }
    
    @Override
    public String toString() {

        StringWriter writer;
        PrintWriter  printer;

        writer  = new StringWriter();
        printer = new PrintWriter(writer);
        
        printer.printf("[%s]%n", this.getClass().getName());
        printer.printf("  leap indicator       = %s%n", String.valueOf(this.leapIndicator));
        printer.printf("  version              = %s%n", String.valueOf(this.version));
        printer.printf("  mode                 = %s%n", String.valueOf(this.mode));
        printer.printf("  stratum              = %s%n", String.valueOf(this.stratum));
        printer.printf("  poll interval        = %s%n", String.valueOf(this.pollInterval));
        printer.printf("  precision            = %s%n", String.valueOf(this.precision));
        printer.printf("  root delay           = %.2f%n ms", Double.valueOf(this.rootDelay *1000));
        printer.printf("  root dispersion      = %.2f%n ms", Double.valueOf(this.rootDispersion *1000));
        printer.printf("  reference identifier = %s%n", this.getReferenceIdentifier());
        printer.printf("  reference timestamp  = %tF %<tT.%<tL%n", new Date(this.getReferenceTimestamp()));
        printer.printf("  originate timestamp  = %tF %<tT.%<tL%n", new Date(this.getOriginateTimestamp()));
        printer.printf("  receive timestamp    = %tF %<tT.%<tL%n", new Date(this.getReceiveTimestamp()));
        printer.printf("  transmit timestamp   = %tF %<tT.%<tL%n", new Date(this.getTransmitTimestamp()));
        
        return writer.toString();
    }
}