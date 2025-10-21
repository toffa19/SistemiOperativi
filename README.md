# LABSO — P2P con Master Registry (A.A. 2024–25)

Sistema **peer-to-peer** con **registro centrale (Master)**.  
I peer si registrano presso il Master pubblicando le risorse locali; quando un peer vuole scaricare una risorsa, chiede al Master un **token** e un **peer sorgente**. Il trasferimento è **line-based + binario**: header testuale, poi i byte del file. La concorrenza è gestita con **mutua esclusione** sia lato Master (registro) sia lato Peer (server serializzato).

---

## Sommario
- [Architettura in breve](#architettura-in-breve)
- [Prerequisiti](#prerequisiti)
- [Build](#build)
- [Esecuzione (quickstart)](#esecuzione-quickstart)
- [CLI: comandi](#cli-comandi)
- [Protocollo di rete](#protocollo-di-rete)
- [Test rapidi](#test-rapidi)
- [Troubleshooting](#troubleshooting)
- [Struttura del repository](#struttura-del-repository)
- [Scelte progettuali](#scelte-progettuali)
- [Licenza](#licenza)

---

## Architettura in breve

```
+---------+                         +------------------+
|  Peer A |<-- DOWNLOAD token/file ->|     Peer B       |
| (client)|                         | (server 1-at-a-time)
+----+----+                         +---------+--------+
     ^                                        ^
     |   REGISTER / WHO_HAS / TOKEN           |
     +------------------+---------------------+
                        |
                  +-----v------+
                  |   Master   |
                  | Registry   |
                  | TokenStore |
                  | DownloadLog|
                  +------------+
```

- **Master**
  - Mantiene: `resource -> [peerId]` e `peerId -> PeerRef(host,port)`.
  - Emette/revoca **token** per i download (con TTL).
  - Tiene un **log** (OK/FAIL) degli eventi di download.

- **Peer**
  - Si **registra** al Master con le risorse locali.
  - Espone un **server TCP** che serve **una richiesta alla volta** (`Semaphore(1)`).
  - Come **client**, chiede token/sorgenti al Master e scarica i file dai peer remoti.

---

## Prerequisiti
- **Java 21+**
- **Maven 3.9+**
- Porta libera per il Master (default **9000**)

---

## Build

```bash
mvn clean package
```

JAR “fat/uber” tipici (aggiorna i nomi se il `pom.xml` differisce):
- **Master:** `target/labso-master-1.0.0-all.jar`
- **Peer/Client:** `target/labso-client-1.0.0-all.jar`

---

## Esecuzione (quickstart)

### Windows (PowerShell/CMD)
```bat
:: Shell 1 — Master sulla porta 9000
java -jar target\labso-master-1.0.0-all.jar 9000

:: Shell 2 — Peer A collegato al Master
java -jar target\labso-client-1.0.0-all.jar 127.0.0.1 9000

:: Shell 3 — Peer B collegato al Master (opzionale)
java -jar target\labso-client-1.0.0-all.jar 127.0.0.1 9000
```

### Unix/macOS (bash/zsh)
```sh
# Shell 1 — Master
java -jar target/labso-master-1.0.0-all.jar 9000

# Shell 2 — Peer A
java -jar target/labso-client-1.0.0-all.jar 127.0.0.1 9000

# Shell 3 — Peer B (opzionale)
java -jar target/labso-client-1.0.0-all.jar 127.0.0.1 9000
```

> In `run/` sono inclusi script `.sh` e `.bat` per avvio rapido.

---

## CLI: comandi

### Master (console)
- `listdata` — stampa `resource -> [peerId]`
- `inspectNodes` — stampa `peerId -> PeerRef(host,port)`
- `log` — eventi di download (OK/FAIL)
- `quit` — arresto pulito

### Peer (console)
- `listdata local` — elenca file locali
- `listdata remote` — indice globale dal Master
- `listpeers` — elenca peer registrati
- `whohas <nome>` — mostra i peer che possiedono `<nome>`
- `add <nome> <contenuto>` — crea file locale e aggiorna il Master
- `download <nome>` — scarica con token e retry
- `quit` — deregistra il peer e chiude

---

## Protocollo di rete

**Formato (UTF-8 line-based):**
- Richieste: `CMD [JSON]`
- Risposte Master: `OK [JSON]` **oppure** `ERR <motivo>`

**Peer → Master**
- `REGISTER` — registra/aggiorna peer e risorse locali
- `LISTDATA_REMOTE` — mappa globale `risorsa -> [peerId]`
- `LIST_PEERS` — mappa `peerId -> PeerRef`
- `WHO_HAS {"resource":"<nome>"}` — lista di `PeerRef`
- `DOWNLOAD_TOKEN_REQ {"resource","requesterPeerId"}` → token + peer sorgente
- `DOWNLOAD_TOKEN_REL {"token","resource","fromPeerId","requesterPeerId"}` — rilascio token
- `DOWNLOAD_FAILED {"resource","fromPeerId","requesterPeerId"}` — pulizia registro e log FAIL
- `PEER_QUIT {"peerId"}` — deregistrazione

**Peer → Peer**
- `DOWNLOAD <nome> <token>` → `OK <size>` + **esattamente** `size` byte

**Sequenza download (con retry)**
1. Peer chiede token al Master (`DOWNLOAD_TOKEN_REQ`).
2. Master risponde con `token` e un `PeerRef` sorgente.
3. Peer invia `DOWNLOAD <nome> <token>` al sorgente.
4. Se fallisce: invia `DOWNLOAD_FAILED` al Master e ritenta con altro candidato.
5. Se ok: salva file e invia `DOWNLOAD_TOKEN_REL` al Master.

---

## Test rapidi

1) **Visibilità risorse**
- Peer A: `listdata local`, `add R3.txt hello`, `listdata remote`
- Peer B: `listdata remote`, `whohas R3.txt`, `download R3.txt`
- Master: `log` deve contenere un evento **OK**.

2) **Retry**
- Durante un download, chiudi il peer sorgente: il client invia `DOWNLOAD_FAILED` e ritenta (se ci sono altri candidati).

3) **Mutua esclusione Peer**
- Avvia due download verso lo stesso peer: uno parte solo quando finisce l’altro (`Semaphore(1)`).

---

## Troubleshooting

- **Connection refused/timeout** → Master non avviato o host/porta errati. Avvia `java -jar ... 9000` e usa `127.0.0.1 9000` lato peer.
- **Download bloccato** → Verifica header `OK <size>` dal sorgente; controlla firewall e che il `PeerServer` sia partito.
- **CRLF/LF su Windows** → Aggiungi `.gitattributes`:
  ```gitattributes
  * text=auto
  *.sh text eol=lf
  *.bat text eol=crlf
  ```

---

## Struttura del repository

```
.
├─ pom.xml
├─ README.md
├─ run/
│  ├─ start-master.sh / start-master.bat
│  ├─ start-peer-a.sh / start-peer-a.bat
│  └─ start-peer-b.sh / start-peer-b.bat
├─ src/
│  ├─ main/java/it/unibo/labso/p2p/common/  # JsonCodec, NetUtils, PeerRef, Token, DTO
│  ├─ main/java/it/unibo/labso/p2p/master/  # MasterMain, MasterServer, ClientHandler, Registry, TokenStore, DownloadLog
│  ├─ main/java/it/unibo/labso/p2p/peer/    # PeerMain, PeerServer, PeerRequestHandler, PeerClient, MasterClient, PeerCli, ResourceManager
│  └─ main/resources/                       # config/logback
└─ target/                                   # jar generati (gitignored)
```

---

## Scelte progettuali

- **Mutua esclusione Master** — `Registry` con **lock unico** (`ReentrantLock`) per impedire letture durante gli update.
- **Mutua esclusione Peer** — server serializzato con **`Semaphore(1)`** (una richiesta per volta).
- **Token con TTL** — emessi dal Master; `DOWNLOAD_TOKEN_REL` revoca esplicita. Su errore: `DOWNLOAD_FAILED` per “ripulire” il registro.
- **Protocollo line-based + JSON** — header umani, trasferimento binario deterministico; semplice da loggare/estendere.

---

## Licenza

Progetto didattico universitario (imposta la licenza che preferisci, es. MIT o Unlicense).
