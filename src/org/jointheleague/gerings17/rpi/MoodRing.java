package org.jointheleague.gerings17.rpi;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

public class MoodRing {

	private I2CBus _i2cbus;
	private I2CDevice _temperatureSensor;
	private ScheduledThreadPoolExecutor _scheduledThreadPoolExecutor;
	private Object _handle;
	private float _temperatureRef = Float.MIN_VALUE;
	
	//http://www.programcreek.com/java-api-examples/index.php?source_dir=iot-server-appliances-master/WSO2Agents/wso2agents-mgt/org.wso2.carbon.device.mgt.iot.agent.kura.firealarm/org.wso2.carbon.device.mgt.iot.agent.kura.firealarm.real/src/main/java/org/wso2/carbon/device/mgt/iot/agent/kura/firealarm/impl/real/operation/AgentOperationManagerImpl.java

	public static void main(String[] args) throws InterruptedException{
		MoodRing mrc = new MoodRing();
		mrc.init();
		float temp = mrc.get_temperatureRef();
		for(int i=0; i<20; i++){
			System.out.println(temp * 1e45);
			Thread.sleep(1000);
		}
	}
	
	private Runnable temperatureReader = new Runnable() {
		@Override
		public void run() {
			try {
				float newTemperature = readTemperature();
				if (Math.abs(_temperatureRef - newTemperature) > .1f) {
					_temperatureRef = newTemperature;
				}
			} catch (IOException e) {

			}
		}
	};

	public float get_temperatureRef() {
		return _temperatureRef;
	}

	public void init() {
		try {
			_i2cbus = I2CFactory.getInstance(I2CBus.BUS_1);
			_temperatureSensor = _i2cbus.getDevice(0x40);
//			_scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
//			_handle = _scheduledThreadPoolExecutor.scheduleAtFixedRate(temperatureReader, 0, 100, TimeUnit.MILLISECONDS);

			// monitor temperature changes
			// every change of more than 0.1C will notify SensorChangedListeners
			
		} catch (IOException e) {

		}
	}

	private synchronized float readTemperature() throws IOException {
		float temperature;
		// Set START (D0) and TEMP (D4) in CONFIG (register 0x03) to begin a
		// new conversion, i.e., write CONFIG with 0x11
		_temperatureSensor.write(0x03, (byte) 0x11);

		// Poll RDY (D0) in STATUS (register 0) until it is low (=0)
		int status = -1;
		while ((status & 0x01) != 0) {
			status = _temperatureSensor.read(0x00);
		}

		// Read the upper and lower bytes of the temperature value from
		// DATAh and DATAl (registers 0x01 and 0x02), respectively
		byte[] buffer = new byte[3];
		_temperatureSensor.read(buffer, 0, 3);

		int dataH = buffer[1] & 0xff;
		int dataL = buffer[2] & 0xff;

		// s_logger.info("I2C: [{}, {}]", new Object[] {dataH, dataL} );

		temperature = (dataH * 256 + dataL) >> 2;
		temperature = (temperature / 32f) - 50f;

		// s_logger.info("Temperature: {}", temperature);

		// truncate to 2 decimals
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		return Float.valueOf(twoDForm.format(temperature));
	}

}
