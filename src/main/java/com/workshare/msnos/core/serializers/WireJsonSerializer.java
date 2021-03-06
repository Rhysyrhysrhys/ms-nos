package com.workshare.msnos.core.serializers;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.workshare.msnos.core.Iden;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.Message.Payload;
import com.workshare.msnos.core.MessageBuilder;
import com.workshare.msnos.core.Version;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.GenericPayload;
import com.workshare.msnos.core.payloads.HealthcheckPayload;
import com.workshare.msnos.core.payloads.NullPayload;
import com.workshare.msnos.core.payloads.PongPayload;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.payloads.TracePayload;
import com.workshare.msnos.core.protocols.ip.BaseEndpoint;
import com.workshare.msnos.core.protocols.ip.Endpoint;
import com.workshare.msnos.core.protocols.ip.HttpEndpoint;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.soup.json.ThreadSafeGson;
import com.workshare.msnos.usvc.api.RestApi;

public class WireJsonSerializer implements WireSerializer {

    private static Logger log = LoggerFactory.getLogger(WireSerializer.class);

    @Override
    public <T> T fromText(String text, Class<T> clazz) {
        try {
            return gson.fromJson(text, clazz);
        } catch (JsonSyntaxException ex) {
            log.warn("Error parsing JSON content: {}", text);
            throw ex;
        }
    }

    @Override
    public String toText(Object anyObject) {
        return gson.toJson(anyObject);
    }

    @Override
    public <T> T fromReader(Reader reader, Class<T> clazz) {
        return gson.fromReader(reader, clazz);
    }

    @Override
    public <T> T fromBytes(byte[] array, Class<T> clazz) {
        return fromText(new String(array, Charset.forName("UTF-8")), clazz);
    }

    @Override
    public <T> T fromBytes(byte[] array, int offset, int length, Class<T> clazz) {
        return fromText(new String(array, offset, length, Charset.forName("UTF-8")), clazz);
    }

    @Override
    public byte[] toBytes(Object anyObject) {
        final String json = gson.toJson(anyObject);
        return json.getBytes(Charset.forName("UTF-8"));
    }

    private static final JsonSerializer<Boolean> ENC_BOOL = new JsonSerializer<Boolean>() {
        @Override
        public JsonElement serialize(Boolean value, Type typeof, JsonSerializationContext context) {
            return new JsonPrimitive(value ? 1 : 0);
        }
    };

    private static final JsonDeserializer<Boolean> DEC_BOOL = new JsonDeserializer<Boolean>() {
        @Override
        public Boolean deserialize(JsonElement json, Type typeof, JsonDeserializationContext context)
                throws JsonParseException {
            return json.getAsInt() == 0 ? Boolean.FALSE : Boolean.TRUE;
        }
    };

    private static final JsonSerializer<Byte> ENC_BYTE = new JsonSerializer<Byte>() {
        @Override
        public JsonElement serialize(Byte value, Type typeof, JsonSerializationContext context) {
            return new JsonPrimitive((int) (value & 0xff));
        }
    };

    private static final JsonSerializer<UUID> ENC_UUID = new JsonSerializer<UUID>() {
        @Override
        public JsonElement serialize(UUID uuid, Type typeof, JsonSerializationContext context) {
            return new JsonPrimitive(serializeUUIDToShortString(uuid));
        }
    };

    private static final JsonDeserializer<UUID> DEC_UUID = new JsonDeserializer<UUID>() {
        @Override
        public UUID deserialize(JsonElement json, Type typeof, JsonDeserializationContext context)
                throws JsonParseException {
            return deserializeUUIDFromShortString(json.getAsString());
        }
    };

    private static final JsonSerializer<Iden> ENC_IDEN = new JsonSerializer<Iden>() {
        @Override
        public JsonElement serialize(Iden iden, Type typeof, JsonSerializationContext context) {
            return serializeIden(iden, true);
        }
    };

    private static final JsonDeserializer<Iden> DEC_IDEN = new JsonDeserializer<Iden>() {
        @Override
        public Iden deserialize(JsonElement json, Type typeof, JsonDeserializationContext context)
                throws JsonParseException {
            return deserializeIden(json);
        }
    };

    private static final JsonSerializer<Version> ENC_VERSION = new JsonSerializer<Version>() {
        @Override
        public JsonElement serialize(Version src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getMajor() + "." + src.getMinor());
        }
    };

    private static final JsonDeserializer<Version> DEC_VERSION = new JsonDeserializer<Version>() {
        @Override
        public Version deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            final String text = json.getAsString();
            final int dotIndex = text.indexOf(".");

            final int major = Integer.parseInt(text.substring(0, dotIndex));
            final int minor = Integer.parseInt(text.substring(dotIndex + 1));

            return new Version(major, minor);
        }
    };

    private static final JsonSerializer<Endpoint> ENC_ENDPOINT = new JsonSerializer<Endpoint>() {
        @Override
        public JsonElement serialize(Endpoint src, Type typeOfSrc, JsonSerializationContext context) {
            final String network = context.serialize(src.getNetwork()).getAsString();
            final Endpoint.Type type = src.getType();
            if (typeOfSrc == HttpEndpoint.class) {
                String url = ((HttpEndpoint) src).getUrl();
                return new JsonPrimitive(type + "," + src.getPort()+","+network+","+url);
            }
            else {
                return new JsonPrimitive(type + "," + src.getPort()+","+network);
            }
        }
    };

    private static final JsonDeserializer<Endpoint> DEC_ENDPOINT = new JsonDeserializer<Endpoint>() {
        @Override
        public Endpoint deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {

            final String text = json.getAsString();
            final String[] tokens = text.split(",");
            
            final Endpoint.Type type = Endpoint.Type.valueOf(tokens[0]);
            final short port = Short.parseShort(tokens[1]);
            final Network network = context.deserialize(new JsonPrimitive(tokens[2]), Network.class);

            if (Endpoint.Type.HTTP.equals(type)) {
                return new HttpEndpoint(network, tokens[3]);
            } else
                return new BaseEndpoint(type, network, port);
        }
    };

    private static final JsonSerializer<Network> ENC_NETWORK = new JsonSerializer<Network>() {
        @Override
        public JsonElement serialize(Network src, Type typeOfSrc, JsonSerializationContext context) {
            StringBuilder sb = new StringBuilder();
            for (byte b : src.getAddress()) {
                sb.append(b);
                sb.append('.');
            }
            sb.append(src.getPrefix());
            return new JsonPrimitive(sb.toString());
        }
    };

    private static final JsonDeserializer<Network> DEC_NETWORK = new JsonDeserializer<Network>() {
        @Override
        public Network deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {

            final String text = json.getAsString();
            final String[] tokens = text.split("\\.");
            int index = 0;

            final byte[] address = new byte[tokens.length-1];
            for (; index < address.length; index++) {
                address[index] = Byte.valueOf(tokens[index]);
            }

            final short prefix = Short.valueOf(tokens[index]);
            
            return new Network(address, prefix);
        }
    };

    private static final JsonSerializer<RestApi> ENC_RESTAPI = new JsonSerializer<RestApi>() {
        @Override
        public JsonElement serialize(RestApi api, Type typeOfSrc, JsonSerializationContext context) {
            final JsonObject res = new JsonObject();
            res.add("ty", context.serialize(api.getType()));
            res.addProperty("pa", api.getPath());
            res.addProperty("ho", api.getHost());
            res.addProperty("po", api.getPort());
            res.addProperty("st", api.hasAffinity());
            res.addProperty("xp", api.getPriority());
            return res;
        }
    };

    private static final JsonDeserializer<RestApi> DEC_RESTAPI = new JsonDeserializer<RestApi>() {
        @Override
        public RestApi deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = json.getAsJsonObject();
            
            RestApi.Type type = context.deserialize(obj.get("ty"), RestApi.Type.class);
            boolean sticky = obj.get("st").getAsBoolean();
            int priority = obj.get("xp").getAsInt();
            String path = getString(obj,"pa");
            String host = getString(obj,"ho");
            int port = obj.get("po").getAsInt();
            
            return new RestApi(path, port, host, type, sticky, priority);
        }
    };

    private static final JsonSerializer<Message> ENC_MESSAGE = new JsonSerializer<Message>() {
        @Override
        public JsonElement serialize(Message msg, Type typeof, JsonSerializationContext context) {
            final JsonObject res = new JsonObject();
            res.add("v", context.serialize(msg.getVersion()));
            res.add("fr", context.serialize(msg.getFrom()));
            res.add("to", serializeIden(msg.getTo(), false));
            res.add("rx", context.serialize(msg.isReliable()));
            res.addProperty("hp", msg.getHops());
            res.addProperty("ty", msg.getType().toString());
            res.addProperty("ss", msg.getSig());
            res.addProperty("rr", msg.getRnd());
            res.addProperty("ts", msg.getWhen());
            res.add("id", context.serialize(msg.getUuid()));
            if (!(msg.getData() instanceof NullPayload))
                res.add("dt", context.serialize(msg.getData()));

            return res;
        }
    };

    private static final JsonDeserializer<Message> DEC_MESSAGE = new JsonDeserializer<Message>() {
        @Override
        public Message deserialize(JsonElement json, Type typeof, JsonDeserializationContext context)
                throws JsonParseException {
            final JsonObject obj = json.getAsJsonObject();

            final UUID uuid;
            if (obj.get("id") != null) uuid = context.deserialize(obj.get("id").getAsJsonPrimitive(), UUID.class);
            else uuid = null;

            final Message.Type type = Message.Type.valueOf(obj.get("ty").getAsString());
            final Iden from = context.deserialize(obj.get("fr").getAsJsonPrimitive(), Iden.class);
            final Iden to = context.deserialize(obj.get("to").getAsJsonPrimitive(), Iden.class);
            final int hops = obj.get("hp").getAsInt();
            final Boolean reliable = context.deserialize(obj.get("rx"), Boolean.class);
            final String sig = getString(obj, "ss");
            final String rnd = getString(obj, "rr");
            final long when = obj.get("ts").getAsLong();

            Payload data = null;
            JsonElement dataJson = obj.get("dt");
            if (dataJson != null) {
                switch (type) {
                    case ACK:
                        try {data = (Payload) gson.fromJsonTree(dataJson, TracePayload.class);}
                        catch (Exception ignore) {}
                        break;
                    case PRS:
                        data = (Payload) gson.fromJsonTree(dataJson, Presence.class);
                        break;
                    case QNE:
                        data = (Payload) gson.fromJsonTree(dataJson, QnePayload.class);
                        break;
                    case FLT:
                        data = (Payload) gson.fromJsonTree(dataJson, FltPayload.class);
                        break;
                    case HCK:
                        data = (Payload) gson.fromJsonTree(dataJson, HealthcheckPayload.class);
                        break;
                    case PON:
                        data = (Payload) gson.fromJsonTree(dataJson, PongPayload.class);
                        break;
                    case TRC:
                        data = (Payload) gson.fromJsonTree(dataJson, TracePayload.class);
                        break;
                    default:
                        data = (dataJson == null ? NullPayload.INSTANCE : new GenericPayload(dataJson));
                        break;
                }
            }

            return new MessageBuilder(type, from, to)
                    .withHops(hops)
                    .with(data)
                    .with(uuid)
                    .at(when)
                    .reliable(reliable)
                    .signed(sig, rnd)
                    .make();
        }
    };

    private static final ThreadSafeGson gson = new ThreadSafeGson() {
        protected Gson newGson() {
            GsonBuilder builder = new GsonBuilder();

            builder.registerTypeAdapter(Boolean.class, ENC_BOOL);
            builder.registerTypeAdapter(boolean.class, ENC_BOOL);
            builder.registerTypeAdapter(Boolean.class, DEC_BOOL);
            builder.registerTypeAdapter(boolean.class, DEC_BOOL);

            builder.registerTypeAdapter(Byte.class, ENC_BYTE);
            builder.registerTypeAdapter(byte.class, ENC_BYTE);

            builder.registerTypeAdapter(Iden.class, ENC_IDEN);
            builder.registerTypeAdapter(Iden.class, DEC_IDEN);

            builder.registerTypeAdapter(UUID.class, ENC_UUID);
            builder.registerTypeAdapter(UUID.class, DEC_UUID);

            builder.registerTypeAdapter(Network.class, ENC_NETWORK);
            builder.registerTypeAdapter(Network.class, DEC_NETWORK);

            builder.registerTypeAdapter(Version.class, ENC_VERSION);
            builder.registerTypeAdapter(Version.class, DEC_VERSION);

            builder.registerTypeAdapter(Message.class, ENC_MESSAGE);
            builder.registerTypeAdapter(Message.class, DEC_MESSAGE);

            builder.registerTypeAdapter(RestApi.class, ENC_RESTAPI);
            builder.registerTypeAdapter(RestApi.class, DEC_RESTAPI);

            builder.registerTypeAdapter(Endpoint.class, DEC_ENDPOINT);
            builder.registerTypeAdapter(Endpoint.class, ENC_ENDPOINT);
            builder.registerTypeAdapter(BaseEndpoint.class, ENC_ENDPOINT);
            builder.registerTypeAdapter(HttpEndpoint.class, ENC_ENDPOINT);

            return builder.create();
        }
    };

    private static final JsonPrimitive serializeIden(Iden iden, boolean includeSuid) {
        String text = iden.getType() + ":" + serializeUUIDToShortString(iden.getUUID());
        return new JsonPrimitive(text);
    }

    private static final Iden deserializeIden(JsonElement json) {
        String text = json.getAsString();

        int idx1 = text.indexOf(':');
        int idx2 = text.indexOf(':', idx1 + 1);
        idx2 = (idx2 > 0 ? idx2 : text.length());

        Iden.Type type = Iden.Type.valueOf(text.substring(0, idx1));
        UUID uuid = deserializeUUIDFromShortString(text.substring(idx1 + 1, idx2));

        return new Iden(type, uuid);
    }

    private static String serializeUUIDToShortString(UUID uuid) {
        return uuid.toString().replaceAll("-", "");
    }

    private static UUID deserializeUUIDFromShortString(String text) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(text.substring(0, 8));
            sb.append('-');
            sb.append(text.substring(8, 12));
            sb.append('-');
            sb.append(text.substring(12, 16));
            sb.append('-');
            sb.append(text.substring(16, 20));
            sb.append('-');
            sb.append(text.substring(20));

            return UUID.fromString(sb.toString());
        } catch (Exception any) {
            throw new JsonParseException(any);
        }
    }

    private static final String getString(final JsonObject obj, final String memberName) {
        final JsonElement jsonElement = obj.get(memberName);
        return (jsonElement == null) ? null : jsonElement.getAsString();
    }

    static class Sample {
        String name = "alfa";
        boolean res = true;

        public String toString() {
            return Json.toJsonString(this);
        }
    }
}
