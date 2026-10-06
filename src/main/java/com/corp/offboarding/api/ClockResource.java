package com.corp.offboarding.api;

import com.corp.offboarding.service.TimeService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** 模拟业务时钟控制（到期行为演示用）。 */
@Path("/api/clock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClockResource {

    @Inject
    TimeService time;

    @GET
    public Map<String, Object> get() {
        return Map.of("now", time.now());
    }

    @POST
    public Map<String, Object> set(JsonNode body) {
        long now;
        if (body.hasNonNull("epoch")) {
            now = time.setTime(body.get("epoch").asLong());
        } else if (body.hasNonNull("advanceMinutes")) {
            now = time.advanceMinutes(body.get("advanceMinutes").asLong());
        } else {
            throw new BadRequestException("需要 epoch 或 advanceMinutes");
        }
        return Map.of("now", now);
    }
}
