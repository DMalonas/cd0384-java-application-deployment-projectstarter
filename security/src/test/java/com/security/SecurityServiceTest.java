package com.security;

import static org.junit.jupiter.api.Assertions.*;

import com.security.data.*;
import com.security.service.SecurityService;
import com.service.img.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author DMalonas
 */
@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private Sensor sensor;
    private final String randomString = UUID.randomUUID().toString();

    @Mock
    SecurityRepository securityRepository;

    @Mock
    ImageService imageService;

    private
    SecurityService securityService;

    private Sensor getNewSensor(){
        return new Sensor(randomString, SensorType.DOOR);
    }

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = getNewSensor();
    }

    static Stream<Arguments> booleanMethodSource(){
        return Stream.of(
                Arguments.of(false, true),
                Arguments.of(true, false)
        );
    }

    private Set<Sensor> getSensors(boolean active, int count){
        String randomString = UUID.randomUUID().toString();

        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i <= count; i++){
            sensors.add(new Sensor(randomString, SensorType.DOOR));
        }
        sensors.forEach(it -> it.setActive(active));
        return sensors;
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeAlarmStatus_alarmArmedAndSensorActivated_alarmStatusPending(ArmingStatus armingStatus){
        when(securityRepository.getSensors())
                .thenReturn(getSensors(true, 2));
        when(securityService.getArmingStatus())
                .thenReturn(armingStatus);
        when(securityService.getAlarmStatus())
                .thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.PENDING_ALARM);
    }

    @Test
    void changeAlarmStatus_alarmPendingAndAllSensorsInactive_changeToNoAlarm(){
        Set<Sensor> allSensors = getSensors(false, 4);
        when(securityRepository.getSensors())
                .thenReturn(allSensors);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.NO_ALARM);
    }

    @Test
    void changeAlarmState_alarmActiveAndSensorStateChanges_stateNotAffected() {
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus())
                .thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never())
                .setAlarmStatus(any(AlarmStatus.class));
        ArgumentCaptor<Sensor> captor = ArgumentCaptor.forClass(Sensor.class);
        verify(securityRepository, atMostOnce())
                .updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never())
                .setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMost(2))
                .updateSensor(captor.capture());
        assertEquals(captor.getValue(), sensor);
    }

    @Test
    void changeAlarmStatus_alarmAlreadyPendingAndSensorActivated_alarmStatusAlarm(){
        when(securityService.getAlarmStatus())
                .thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMost(2))
                .setAlarmStatus(AlarmStatus.ALARM); //first call up
    }

    @ParameterizedTest
    @MethodSource("booleanMethodSource")
    void changeAlarmStatus_sensorStatusChangeAndSystemIsAlreadyDisarmed_stateNotAffected(Boolean activeStatus1, Boolean activeStatus2){
        sensor.setActive(activeStatus1);
        when(securityRepository.getArmingStatus())
                .thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor, activeStatus2);
        verify(securityRepository, never())
                .setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMostOnce())
                .updateSensor(sensor);
    }

    @Test
    void changeAlarmState_systemActivatedWhileAlreadyActiveAndAlarmPending_changeToAlarmState(){
        when(securityRepository.getAlarmStatus())
                .thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM); //fix code
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void changeAlarmState_sensorDeactivateWhileInactive_noChangeToAlarmState(AlarmStatus alarmStatus){
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never())
                .setAlarmStatus(any());
    }

    @Test
    void changeAlarmState_noCatImageIdentifiedAndSensorsAreInactive_changeToAlarmStatus(){
        Set<Sensor> inactiveSensors = getSensors(false, 4);
        when(securityRepository.getSensors())
                .thenReturn(inactiveSensors);
        when(imageService.imageContainsCat(any(), anyFloat()))
                .thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.NO_ALARM);
    }

    @Test
    void changeAlarmStatus_systemDisArmed_changeToAlarmStatus(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_AWAY", "ARMED_HOME"})
    void updateSensors_systemArmed_deactivateAllSensors(ArmingStatus armingStatus){
        Set<Sensor> sensors = getSensors(true, 4);
        when(securityRepository.getSensors())
                .thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        List<Executable> executables = new ArrayList<>();
        sensors.forEach(it -> executables.add(() -> assertEquals(it.getActive(), false)));
        assertAll(executables);
    }

    @Test
    void changeAlarmStatus_systemArmedHomeAndCatDetected_changeToAlarmStatus(){
        when(imageService.imageContainsCat(any(), anyFloat()))
                .thenReturn(true);
        when(securityRepository.getArmingStatus())
                .thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

    @Test
    void changeAlarmState_imageContainingCatDetectedAndSystemArmed_changeToAlarmStatus(){
        when(securityRepository.getArmingStatus())
                .thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat()))
                .thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        ArgumentCaptor<AlarmStatus> captor = ArgumentCaptor.forClass(AlarmStatus.class);
        verify(securityRepository, atMostOnce())
                .setAlarmStatus(captor.capture());
        assertEquals(captor.getValue(), AlarmStatus.ALARM);
    }

}
