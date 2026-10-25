package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.access.dto.request.AssignControllerRequest;
import org.gcrel.tfbackevent.access.exception.ControllerAlreadyAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerNotAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerUserNotFoundException;
import org.gcrel.tfbackevent.access.service.EventStaffService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.repository.EventParticipationRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.EventParticipation;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class EventStaffServiceTest {
    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventParticipationRepository participationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventStaffService eventStaffService;

    private User organizer;
    private User controller;
    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        organizer = user("organizer@example.com");
        controller = user("controller@example.com");
        controller.setEnabled(true);
        event = new InPersonEvent(
                "Conference", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("20.00"), "Address",
                new BigDecimal("-34.60"), new BigDecimal("-58.38"), 100);
        event.setId(UUID.randomUUID());
        event.setStatus(EventStatus.PUBLISHED);
    }

    @Test
    void assign_NormalizesEmailAndFlushesParticipation() {
        stubAssignable();
        var response = eventStaffService.assign(
                event.getId(),
                new AssignControllerRequest("  CONTROLLER@EXAMPLE.COM "),
                organizer);

        assertEquals(controller.getId(), response.userId());
        verify(userRepository).findByEmail("controller@example.com");
        ArgumentCaptor<EventParticipation> captor = ArgumentCaptor.forClass(EventParticipation.class);
        verify(participationRepository).saveAndFlush(captor.capture());
        assertEquals(controller, captor.getValue().getUser());
        assertEquals(event, captor.getValue().getEvent());
        assertEquals(EventRole.CONTROLLER, captor.getValue().getId().getEventRole());
    }

    @Test
    void assign_WhenConcurrentInsertViolatesUniqueKey_ReturnsAlreadyAssigned() {
        stubAssignable();
        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(ControllerAlreadyAssignedException.class, () -> eventStaffService.assign(
                event.getId(), new AssignControllerRequest("controller@example.com"), organizer));
    }

    @Test
    void assign_WhenOrganizerAssignsThemself_ThrowsBadRequest() {
        stubOrganizer();
        organizer.setEnabled(true);
        when(userRepository.findByEmail(organizer.getEmail())).thenReturn(Optional.of(organizer));
        assertThrows(BadRequestException.class, () -> eventStaffService.assign(
                event.getId(), new AssignControllerRequest(organizer.getEmail()), organizer));
    }

    @Test
    void assign_WhenAlreadyAssigned_ThrowsConflict() {
        stubOrganizer();
        when(userRepository.findByEmail(controller.getEmail())).thenReturn(Optional.of(controller));
        when(participationRepository.existsByEventAndUserAndId_EventRole(
                event, controller, EventRole.CONTROLLER)).thenReturn(true);
        assertThrows(ControllerAlreadyAssignedException.class, () -> eventStaffService.assign(
                event.getId(), new AssignControllerRequest(controller.getEmail()), organizer));
    }

    @Test
    void assign_WhenEventIsVirtual_ThrowsBadRequest() {
        VirtualEvent webinar = new VirtualEvent(
                "Webinar", "Description", LocalDateTime.now().plusDays(1),
                new BigDecimal("20.00"), "https://meet.example.com", "Meet", null);
        webinar.setId(UUID.randomUUID());
        webinar.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(webinar.getId())).thenReturn(Optional.of(webinar));
        when(participationRepository.existsByEventAndUserAndId_EventRole(
                webinar, organizer, EventRole.ORGANIZER)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> eventStaffService.assign(
                webinar.getId(), new AssignControllerRequest(controller.getEmail()), organizer));
        verify(participationRepository, never()).saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void assign_WhenUserMissing_ThrowsNotFoundWithMissingMessage() {
        stubOrganizer();
        when(userRepository.findByEmail(controller.getEmail())).thenReturn(Optional.empty());
        ControllerUserNotFoundException ex = assertThrows(ControllerUserNotFoundException.class,
                () -> eventStaffService.assign(
                        event.getId(), new AssignControllerRequest(controller.getEmail()), organizer));
        assertEquals("No hay ningún usuario con ese email.", ex.getMessage());
    }

    @Test
    void assign_WhenUserNotVerified_ThrowsNotFoundWithUnverifiedMessage() {
        stubOrganizer();
        User disabled = user("disabled@example.com");
        when(userRepository.findByEmail(disabled.getEmail())).thenReturn(Optional.of(disabled));
        ControllerUserNotFoundException ex = assertThrows(ControllerUserNotFoundException.class,
                () -> eventStaffService.assign(
                        event.getId(), new AssignControllerRequest(disabled.getEmail()), organizer));
        assertEquals("El usuario todavía no verificó su cuenta.", ex.getMessage());
    }

    @Test
    void assign_WhenEventIsCancelled_ThrowsNotModifiable() {
        stubOrganizer();
        event.setStatus(EventStatus.CANCELLED);
        assertThrows(EventNotModifiableException.class, () -> eventStaffService.assign(
                event.getId(), new AssignControllerRequest(controller.getEmail()), organizer));
    }

    @Test
    void assign_WhenUserIsNotOrganizer_ThrowsUnauthorized() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(participationRepository.existsByEventAndUserAndId_EventRole(
                event, organizer, EventRole.ORGANIZER)).thenReturn(false);
        assertThrows(UnauthorizedEventAccessException.class, () -> eventStaffService.assign(
                event.getId(), new AssignControllerRequest(controller.getEmail()), organizer));
    }

    @Test
    void remove_WhenControllerIsNotAssigned_ThrowsNotFound() {
        stubOrganizer();
        when(userRepository.getReferenceById(controller.getId())).thenReturn(controller);
        when(participationRepository.removeParticipation(
                eq(event), eq(controller), eq(EventRole.CONTROLLER))).thenReturn(0);
        assertThrows(ControllerNotAssignedException.class, () -> eventStaffService.remove(
                event.getId(), controller.getId(), organizer));
    }

    @Test
    void list_ReturnsAssignedControllers() {
        stubOrganizer();
        when(participationRepository.findAllByEventAndId_EventRole(event, EventRole.CONTROLLER))
                .thenReturn(java.util.List.of(new EventParticipation(
                        controller, event, EventRole.CONTROLLER)));
        assertEquals(1, eventStaffService.list(event.getId(), organizer).size());
    }

    private void stubOrganizer() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(participationRepository.existsByEventAndUserAndId_EventRole(
                event, organizer, EventRole.ORGANIZER)).thenReturn(true);
    }

    private void stubAssignable() {
        stubOrganizer();
        controller.setEnabled(true);
        when(userRepository.findByEmail("controller@example.com")).thenReturn(Optional.of(controller));
        when(participationRepository.existsByEventAndUserAndId_EventRole(
                event, controller, EventRole.CONTROLLER)).thenReturn(false);
    }

    private User user(String email) {
        User user = new User("Test User", email, "password", LocalDate.of(1990, 1, 1));
        user.setId(UUID.randomUUID());
        return user;
    }
}
