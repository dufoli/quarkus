package io.quarkus.mongodb.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker build item indicating the MongoClient has been fully initialized.
 */
public final class MongoClientBuildItem extends SimpleBuildItem {

    public MongoClientBuildItem() {
    }
}
