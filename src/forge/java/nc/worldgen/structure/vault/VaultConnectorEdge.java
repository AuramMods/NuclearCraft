package nc.worldgen.structure.vault;

final class VaultConnectorEdge {

    final int fromNodeId;
    final int toNodeId;
    final VaultConnectorType connectorType;

    VaultConnectorEdge(int fromNodeId, int toNodeId, VaultConnectorType connectorType) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.connectorType = connectorType;
    }
}
