package com.security.service;

import com.security.application.StatusListener;
import com.security.data.AlarmStatus;
import com.security.data.ArmingStatus;
import com.security.data.SecurityRepository;
import com.security.data.Sensor;
import com.service.img.ImageService;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private ImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if(armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if (isSystemArmed(armingStatus)){
            changeActivationStatusForSensors(getSensors(), false);
        }
        securityRepository.setArmingStatus(armingStatus);
    }

    private void changeActivationStatusForSensors(Set<Sensor> sensors, boolean active) {
        sensors.forEach(it -> {
            System.out.println("Sensor is active before " + it.getActive());
        });

        System.out.println("Alarm pending and system activated");
        sensors.stream().forEach(it -> changeSensorActivationStatus(it, active));

        System.out.println("\n");
        sensors.forEach(it -> {
            System.out.println("Sensor is active " + it.getActive());
        });

    }

    boolean isSystemArmed(ArmingStatus armingStatus){
        return List.of(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY)
                .contains(armingStatus);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        if(cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!cat && allSensorsInActiveState(false)){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        if (status == AlarmStatus.PENDING_ALARM && allSensorsInActiveState(false)){
            securityRepository.setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            securityRepository.setAlarmStatus(status); 
        }
        statusListeners.forEach(sl -> sl.notify(status));
    }

    private boolean allSensorsInActiveState(boolean activeState) {
        return getSensors().stream().allMatch(it -> it.getActive()==activeState);
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void manageActivatedSensor() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch(securityRepository.getAlarmStatus()) {
			case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
			case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
		    default -> {System.out.println("default");}
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void manageDeactivatedSensor() {
        if (securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        boolean sensorActive = sensor.getActive();
        Boolean activateSensor = !sensorActive && active;
        Boolean deaActivateSensor = sensorActive && !active;
        if (activateSensor || deaActivateSensor){
            if (activateSensor){
                manageActivatedSensor();
            } else {
                manageDeactivatedSensor();
            }
        }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
