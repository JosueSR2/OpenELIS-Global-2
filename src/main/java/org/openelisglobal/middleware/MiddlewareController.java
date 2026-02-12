package org.openelis.middleware;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
}
