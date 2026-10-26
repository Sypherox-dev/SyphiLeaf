# SyphiLeaf – Änderungsprotokoll & Dokumentation

**Datum:** 26. Oktober 2026  
**Commit:** [`d2729231`](https://github.com/Sypherox-dev/SyphiLeaf/commit/d2729231)  
**Branch:** `ver/1.21.11`  
**Repository:** [Sypherox-dev/SyphiLeaf](https://github.com/Sypherox-dev/SyphiLeaf.git)

---

## 1. Fehlerbehebung: `ProtocolInfoBuilder.java` (Netzwerkprotokoll)

### Problem
Beim Öffnen und Analysieren von `leaf-server/src/minecraft/java/net/minecraft/network/protocol/ProtocolInfoBuilder.java` trat in der Methode `withBundlePacket` folgender Typkonvertierungsfehler im Eclipse JDT Compiler (Language Server der IDE) auf:

```text
Cannot cast from PacketType<capture#... of ? extends BundleDelimiterPacket<? super T>> to PacketType<D>
```

### Ursache
`D` ist als generischer Typ definiert (`D extends BundleDelimiterPacket<? super T>`). `packet.type()` liefert per Deklaration `PacketType<? extends BundleDelimiterPacket<? super T>>`. Der Eclipse-Compiler weigerte sich wegen strenger Capture-Conversion, diesen Wildcard-Typ direkt in den parametrisierten Typ `PacketType<D>` zu casten. Ein temporärer Cast über `(Object)` umging zwar die Prüfung, war jedoch unsauber und löste Warnungen aus.

### Lösung
Der Cast wurde mit einem typisierten Wildcard-Zwischencast und `@SuppressWarnings("unchecked")` sauber strukturiert, sodass sowohl OpenJDK `javac` als auch Eclipse JDT den Code fehler- und warnungsfrei kompilieren:

```diff
--- a/net/minecraft/network/protocol/ProtocolInfoBuilder.java
+++ b/net/minecraft/network/protocol/ProtocolInfoBuilder.java
@@ -41,7 +41,8 @@ public class ProtocolInfoBuilder<T extends PacketListener, B extends ByteBuf, C>
         PacketType<P> type, Function<Iterable<Packet<? super T>>, P> bundler, D packet
     ) {
         StreamCodec<ByteBuf, D> streamCodec = StreamCodec.unit(packet);
-        PacketType<D> packetType = (PacketType<D>)packet.type();
+        @SuppressWarnings("unchecked")
+        PacketType<D> packetType = (PacketType<D>) (PacketType<?>) packet.type();
         this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packetType, streamCodec, null));
         this.bundlerInfo = BundlerInfo.createForPacket(type, bundler, packet);
         return this;
```

---

## 2. Neuer Minecraft Feature-Patch (Paperweight)

Da `leaf-server/src/minecraft` bei Paperweight-basierten Servern in `.gitignore` liegt und aus Patches generiert wird, wurden alle Änderungen an Minecraft-Quellen in einen sauberen Git-Patch überführt:

**Datei:** `leaf-server/minecraft-patches/features/0338-SyphiLeaf-optimizations-and-protocol-fixes.patch`

### Enthaltene Anpassungen:
1. **`ProtocolInfoBuilder.java`**: Der oben beschriebene Typen-Cast-Fix.
2. **`Connection.java`**: Hook in `configurePacketHandler` zur Registrierung des `NettyInboundInterceptor` in der Netty-Pipeline:
   ```java
   // SyphiLeaf start - Inbound AntiCheat & Rate-Limit Interceptor
   org.dreeam.leaf.network.NettyInboundInterceptor.inject(pipeline, this);
   // SyphiLeaf end - Inbound AntiCheat & Rate-Limit Interceptor
   ```
3. **`ChunkEntitySlices.java`**: Bereitstellung der Hilfsmethode `getEntityCount()` für das Collision-Culling:
   ```java
   // SyphiLeaf start - Dense collision culler
   public int getEntityCount() {
       return this.entities.size();
   }
   // SyphiLeaf end - Dense collision culler
   ```

---

## 3. Neue SyphiLeaf Optimierungs- & AntiCheat-Module

Folgende neue Komponenten in `leaf-server/src/main/java/org/dreeam/leaf/` wurden in das Repository aufgenommen:

| Modul | Pfad | Beschreibung |
|---|---|---|
| **HighDensityArenaOptimization** | `config/modules/opt/HighDensityArenaOptimization.java` | Zentrale Konfigurationsklasse für Hochleistungs-Arena- und Massenschlachten-Optimierungen. |
| **AsyncPacketBroadcaster** | `network/AsyncPacketBroadcaster.java` | Zero-Copy Broadcaster mit eigenem Worker-Pool. Serialisiert Broadcast-Pakete exakt einmal und verteilt sie via `retainedDuplicate()` asynchron an Client-EventLoops. |
| **NettyInboundInterceptor** | `network/NettyInboundInterceptor.java` | Pipeline-Handler zur Inbound-Ratenbegrenzung und Abwehr von Paket-Flooding (Anti-Lag / Anti-Exploit). |
| **DenseCollisionCuller** | `world/DenseCollisionCuller.java` | Culling-System für rechenintensive Entity-Kollisionen bei extremen Spieler- und Mob-Ansammlungen. |
| **DirectPaletteFastReset** | `world/DirectPaletteFastReset.java` | Schneller Reset-Mechanismus für Direct-Paletten zur Reduzierung von Garbage-Collection-Overhead. |

---

## 4. Git- und Build-System-Anpassungen

* **`.gitignore`**:
  * Ergänzung von `bin/` und `**/bin/`, um temporäre IDE-Kompilierungsdateien von Eclipse/VS Code zuverlässig aus dem Versionskontrollsystem fernzuhalten.
* **Build-Verifikation**:
  * `./gradlew :leaf-server:compileJava` und `./gradlew :leaf-server:classes` erfolgreich validiert (0 Compiler-Fehler).
* **GitHub Push**:
  * Commit [`d2729231`](https://github.com/Sypherox-dev/SyphiLeaf/commit/d2729231) erstellt und erfolgreich auf `origin ver/1.21.11` gepusht.
