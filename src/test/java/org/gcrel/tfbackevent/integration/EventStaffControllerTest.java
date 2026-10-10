package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.access.controller.EventStaffController;
import org.gcrel.tfbackevent.access.dto.request.AssignControllerRequest;
import org.gcrel.tfbackevent.access.dto.response.ControllerResponse;
import org.gcrel.tfbackevent.access.exception.ControllerAlreadyAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerNotAssignedException;
import org.gcrel.tfbackevent.access.exception.ControllerUserNotFoundException;
import org.gcrel.tfbackevent.access.service.EventStaffService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = EventStaffController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class EventStaffControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventStaffService eventStaffService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private User organizer;
    private UsernamePasswordAuthenticationToken authentication;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        organizer = new User("Organizer", "organizer@example.com", "password", LocalDate.of(1990, 1, 1));
        authentication = new UsernamePasswordAuthenticationToken(
                organizer, null, organizer.getAuthorities());
        objectMapper = new ObjectMapper();
    }

    @Test
    void assign_Returns201AndController() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(eventStaffService.assign(
                eq(eventId), any(AssignControllerRequest.class), eq(organizer)))
                .thenReturn(new ControllerResponse(userId, "Controller", "controller@example.com"));

        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", eventId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("controller@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("controller@example.com"));
    }

    @Test
    void list_ReturnsAssignedControllers() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventStaffService.list(eventId, organizer)).thenReturn(
                List.of(new ControllerResponse(
                        UUID.randomUUID(), "Controller", "controller@example.com")));

        mockMvc.perform(get("/api/v1/events/{eventId}/controllers", eventId)
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Controller"));
    }

    @Test
    void remove_Returns204() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doNothing().when(eventStaffService).remove(eventId, userId, organizer);

        mockMvc.perform(delete("/api/v1/events/{eventId}/controllers/{userId}", eventId, userId)
                .with(authentication(authentication)))
                .andExpect(status().isNoContent());
    }

    @Test
    void assign_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("controller@example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assign_WhenUserMissing_Returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new ControllerUserNotFoundException()).when(eventStaffService)
                .assign(eq(eventId), any(AssignControllerRequest.class), eq(organizer));
        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", eventId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("controller@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_WhenAlreadyAssigned_Returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new ControllerAlreadyAssignedException()).when(eventStaffService)
                .assign(eq(eventId), any(AssignControllerRequest.class), eq(organizer));
        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", eventId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("controller@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    void assign_WhenNotOrganizer_Returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new UnauthorizedEventAccessException()).when(eventStaffService)
                .assign(eq(eventId), any(AssignControllerRequest.class), eq(organizer));
        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", eventId)
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("controller@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_WhenEmailIsInvalid_Returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/controllers", UUID.randomUUID())
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AssignControllerRequest("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void remove_WhenControllerIsNotAssigned_Returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new ControllerNotAssignedException()).when(eventStaffService)
                .remove(eventId, userId, organizer);

        mockMvc.perform(delete("/api/v1/events/{eventId}/controllers/{userId}", eventId, userId)
                .with(authentication(authentication)))
                .andExpect(status().isNotFound());
    }
}
