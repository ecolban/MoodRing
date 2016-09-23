/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jointheleague.gerings17.rpi;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import java.io.IOException;

public class TempSensor {

	// Calibration Data
	private static final int EEPROM_start = 0xAA;
	private static final int NUM_CALIBRATION_BYTES = 22;
	private static final int EEPROM_end = EEPROM_start + NUM_CALIBRATION_BYTES - 1;

	// EEPROM registers - these represent calibration data
	private short ac1;
	private short ac2;
	private short ac3;
	private int ac4;
	private int ac5;
	private int ac6;
	private short b1;
	private short b2;
	private short mb;
	private short mc;

	// Variable common between temperature & pressure calculations
	private int b5;

	// Device address
	private static final int DEVICE_ADDR = 0x77;

	// Temperature Control Register Data
	private static final int CTRL_REGISTER = 0xF4;
	// Temperature read address
	private static final byte TEMP_ADDR = (byte) 0xF6;
	// Read temperature command
	private static final byte GET_TEMP_CMD = (byte) 0x2E;
	// Uncompensated Temperature data
	private int uncompensated_temp_data;

	// I2C bus
	I2CBus bus;
	// Device object
	private I2CDevice bmp180;

	private DataInputStream bmp180CaliIn;
	private DataInputStream bmp180In;
	private volatile boolean running = true;

	public void run() {

		try {
			initialize();

			// read forever till stopped
			while (running) {
				Thread.sleep(1000);
				float celsius = readTemp();
				System.out.println(String.format("Temperature = %0.2f \u2103", celsius));
			}
		} catch (InterruptedException e) {
			System.out.println("Interrupted Exception: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("IOException: " + e.getMessage());
		}
	}

	private void initialize() throws IOException, InterruptedException {
		bus = I2CFactory.getInstance(I2CBus.BUS_1);
		System.out.println("Connected to bus OK!!!");

		// get device itself
		bmp180 = bus.getDevice(DEVICE_ADDR);
		System.out.println("Connected to device OK!!!");

		// Small delay before starting
		Thread.sleep(500);

		// Getting calibration data
		gettingCalibration();
	}

	private void gettingCalibration() {
		try {
			byte[] bytes = new byte[NUM_CALIBRATION_BYTES];

			// read all calibration data into byte array
			int readTotal = bmp180.read(EEPROM_start, bytes, 0, NUM_CALIBRATION_BYTES);
			if (readTotal != 22) {
				System.out.println("Error bytes read: " + readTotal);
			}

			bmp180CaliIn = new DataInputStream(new ByteArrayInputStream(bytes));

			// Read each of the pairs of data as signed short
			ac1 = bmp180CaliIn.readShort();
			ac2 = bmp180CaliIn.readShort();
			ac3 = bmp180CaliIn.readShort();

			// Unsigned short Values
			ac4 = bmp180CaliIn.readUnsignedShort();
			ac5 = bmp180CaliIn.readUnsignedShort();
			ac6 = bmp180CaliIn.readUnsignedShort();

			// Signed sort values
			b1 = bmp180CaliIn.readShort();
			b2 = bmp180CaliIn.readShort();
			mb = bmp180CaliIn.readShort();
			mc = bmp180CaliIn.readShort();
			String calibration = String.format("Callibration: [%d:%d:%d:%d:%d:%d:%d:%d:%d:%d]",
					ac1, ac2, ac3, ac4, ac5, ac6, b1, b2, mb, mc);
			System.out.println(calibration);

		} catch (IOException e) {
			System.out.println("Exception: " + e.getMessage());
		}
	}

	private float readTemp() {

		byte[] tempBytes = new byte[2];

		float celsius = -273.15F; // absolute zero used as default value

		try {
			bmp180.write(CTRL_REGISTER, GET_TEMP_CMD);
			Thread.sleep(500);

			int readTotal = bmp180.read(TEMP_ADDR, tempBytes, 0, 2);
			if (readTotal < 2) {
				System.out.format("Error: %n bytes read/n", readTotal);
			}
			bmp180In = new DataInputStream(new ByteArrayInputStream(tempBytes));
			uncompensated_temp_data = bmp180In.readUnsignedShort();

			// calculate temperature
			int X1 = ((uncompensated_temp_data - ac6) * ac5) >> 15;
			int X2 = (mc << 11) / (X1 + mb);
			b5 = X1 + X2;
			celsius = ((b5 + 8) >> 4) / 10;
			// System.out.println("Temperature u\2103: " + celsius);

		} catch (IOException e) {
			System.out.println("Error reading temp: " + e.getMessage());
		} catch (InterruptedException e) {
			System.out.println("Interrupted Exception: " + e.getMessage());
		}

		return celsius;
	}

	public void stop_running() {
		running = false;
	}

}
