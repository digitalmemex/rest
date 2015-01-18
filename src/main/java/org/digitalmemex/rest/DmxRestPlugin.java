package org.digitalmemex.rest;

import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.AssociationDefinitionModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.Transactional;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

@Path("/rest")
@Produces("application/json")
public class DmxRestPlugin extends PluginActivator {

    private Logger logger = Logger.getLogger(getClass().getName());

    private String host = System.getProperty("dm4.host.url");

    private String uri(String path, Object... params) {
        return String.format(host + path, params);
    }


    private JSONObject toJSON(Topic topic) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", topic.getId());
        json.put("type", topic.getTypeUri());
        if (topic.getUri().isEmpty() == false) {
            json.put("uri", topic.getUri());
        }
        json.put("value", topic.getSimpleValue());

        JSONObject composite = new JSONObject();
        ChildTopics topics = topic.loadChildTopics().getChildTopics();
        for (String childType : topics.getModel()) {
            Object value = topics.get(childType);
            if (value instanceof Topic) {
                composite.put(childType, toJSON((Topic) value));
            } else if (value instanceof List) {
                JSONArray list = new JSONArray();
                for (Topic t : (List<Topic>) value) {
                    list.put(toJSON(t));
                }
                composite.put(childType, list);
            } else {
                throw new RuntimeException("Unexpected value in a ChildTopicsModel: " + value);
            }
        }
        if (composite.length() > 0) {
            json.put("composite", composite);
        }

        JSONObject resources = new JSONObject();
        resources.put("type", uri("/rest/type/%s", topic.getTypeUri()));
        json.put("resources", resources);

        return json;
    }

    private JSONObject toJSON(TopicType type) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", type.getId());
        json.put("type", type.getDataTypeUri());
        json.put("uri", type.getUri());
        json.put("value", type.getSimpleValue());

        JSONArray composite = new JSONArray();
        JSONArray aggregate = new JSONArray();
        for (AssociationDefinitionModel def : type.getModel().getAssocDefs()) {
            JSONObject jsDef = new JSONObject();
            jsDef.put("uri", def.getChildTypeUri());
            jsDef.put("cardinality", def.getChildCardinalityUri());
            if (def.getTypeUri().equals("dm4.core.aggregation_def")) {
                aggregate.put(jsDef);
            } else if (def.getTypeUri().equals("dm4.core.composition_def")) {
                composite.put(jsDef);
            } else {
                logger.warning("invalid association definition model " + def.getTypeUri() +
                        " found in type " + type.getUri());
            }
        }
        json.put("composite", composite);
        json.put("aggregate", aggregate);

        JSONObject resources = new JSONObject();
        resources.put("instances", uri("/rest/topics/%s", type.getUri()));
        resources.put("topic", uri("/rest/topic/%d", type.getId()));
        resources.put("type", uri("/rest/type/%s", type.getUri()));
        json.put("resources", resources);

        return json;
    }

    private JSONArray toJSON(List<RelatedTopic> items) throws JSONException {
        JSONArray list = new JSONArray();
        for (RelatedTopic item : items) {
            JSONObject json = new JSONObject();
            json.put("id", item.getId());
            json.put("type", item.getTypeUri());
            json.put("uri", item.getUri());
            json.put("value", item.getSimpleValue());

            JSONObject resources = new JSONObject();
            resources.put("topic", uri("/rest/topic/%d", item.getId()));
            json.put("resources", resources);

            list.put(json);
        }
        return list;
    }

    private Topic getTopic(long id) {
        Topic topic = dms.getTopic(id);
        if (topic == null) {
            Response response = Response.status(404).entity("topic " + id + " not found").build();
            throw new WebApplicationException(response);
        }
        return topic;
    }

    private TopicType getType(String uri) {
        TopicType type = dms.getTopicType(uri);
        if (type == null) {
            Response response = Response.status(404).entity("type " + uri + " not found").build();
            throw new WebApplicationException(response);
        }
        return type;
    }

    @GET
    @Path("/type/{uri}")
    @Transactional
    public String getTypeJSON(@PathParam("uri") String uri) throws JSONException {
        logger.info("type request " + uri);
        return toJSON(getType(uri)).toString();
    }

    @GET
    @Path("/topic/{id}")
    public String getTopicJSON(@PathParam("id") long id) throws JSONException {
        logger.info("topic request " + id);
        return toJSON(getTopic(id)).toString();
    }

    @GET
    @Path("/topics/{uri}")
    public String getTopicsJSON(@PathParam("uri") String uri) throws JSONException {
        logger.info("topics request " + uri);
        TopicType type = getType(uri);
        ResultList<RelatedTopic> topics = type.getRelatedTopics("dm4.core.instantiation",
                "dm4.core.type", "dm4.core.instance", uri, 0);
        return toJSON(topics.getItems()).toString();
    }

}
