/*
 * Copyright 2026 Bad Monkey, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.badmonkey.agentspaces.a2ui;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An agent's request that its surfaces be served as an application for end users: written into the group's
 * {@code views} space beside the surfaces; an app host in the group takes it, serves the page at an address, and sets
 * the application card's host. {@code surfaces} names the surface ids to serve, or is empty for every surface of the
 * app; {@code host} names a host by name or url, or is empty for the group's default; {@code signOn} is {@code idp}
 * (the identity provider of whoever runs the host; the default) or {@code none} (SPEC §6.1a.4), and a host serves an
 * app without a session only for {@code none}. The newest Publish per app is the one in force. {@code options} carries
 * what a host or a feed may read beyond the record: {@code unpublish: true} takes the page down (the host drops the
 * route, the feed's gate forgets the sign-on); {@code panels} and {@code arrangement} (an application a console
 * drafted: the declared panels in the operator's order, for a feed to synthesize the surface); {@code assembled: true}
 * marks such a Publish. Version 1.
 */
public record Publish(int schemaVersion, String app, String from, List<String> surfaces, String title, String host, String signOn,
                      Map<String, Object> options, Instant at) {

    public static final int VERSION = 1;
    public static final List<String> SIGN_ONS = List.of("idp", "none");

    public Publish {
        Objects.requireNonNull(app, "app");
        if (app.isBlank()) {
            throw new IllegalArgumentException("a publish names its app");
        }
        from = from == null ? "" : from;
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
        title = title == null || title.isBlank() ? app : title;
        host = host == null ? "" : host;
        signOn = signOn == null || signOn.isBlank() ? "idp" : signOn;
        if (!SIGN_ONS.contains(signOn)) {
            throw new IllegalArgumentException("signOn must be one of " + SIGN_ONS + ": " + signOn);
        }
        options = options == null ? Map.of() : Map.copyOf(options);
        Objects.requireNonNull(at, "at");
    }
}
