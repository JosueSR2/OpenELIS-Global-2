package org.openelis.middleware;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

@Path("/middleware")
public class MiddlewareController {

    private final MiddlewareService service = new MiddlewareService();

    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() throws Exception {
        return service.testConnection();
    }

    @GET
    @Path("/results")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getResults() throws Exception {
        return Arrays.asList(service.getResults());
    }

    // 👇 ESTE ES EL NUEVO ENDPOINT CORRECTAMENTE DENTRO DE LA CLASE
    @POST
    @Path("/receive-result")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response receiveResult(String json) {
        System.out.println("Result received from middleware:");
        System.out.println(json);

        return Response.ok("Received").build();
    }
}


