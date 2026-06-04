# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

### Implementación hecha por
- Adrian Ducuara
---

---

## Evidencia de implementación

### Parte I — PrimeFinder con `wait/notify`

**Proyecto:** `wait-notify-excercise_DUCU`

#### Archivos modificados

| Archivo                  | Cambio                                                                                                             |
|--------------------------|--------------------------------------------------------------------------------------------------------------------|
| `pom.xml`                | `maven.compiler.source/target` subido de `1.7` → `11` (JDK moderno requerido)                                      |
| `PrimeFinderThread.java` | Constructor recibe `Control`; `run()` llama `control.awaitIfPaused()` en cada iteración                            |
| `Control.java`           | Reescrito: agrega `pauseLock`, `paused`, `awaitIfPaused()`, `pauseAll()`, `resumeAll()` y loop de pausa en `run()` |

#### Diseño de sincronización

**Monitor único:** `pauseLock` (instancia `Object` en `Control`). Todas las operaciones de pausa/reanudación usan `synchronized(pauseLock)`.

**Flujo:**
```
Control.run()
  └── Thread.sleep(5000)
  └── pauseAll()           → synchronized(pauseLock) { paused = true; }
  └── Thread.sleep(50)     → espera cooperativa mínima
  └── imprime conteos      → lee primes.size() (hilos ya en wait)
  └── System.in.read()     → bloqueo real esperando ENTER (sin busy-wait)
  └── resumeAll()          → synchronized(pauseLock) { paused=false; pauseLock.notifyAll(); }

PrimeFinderThread.run()
  └── for cada número:
        control.awaitIfPaused()
          └── synchronized(pauseLock) { while(paused) pauseLock.wait(); }
        isPrime(i) → agrega a lista
```

**Por qué `while(paused)` y no `if(paused)`:** evita *spurious wakeups* — la JVM puede despertar un hilo en `wait()` sin que nadie llame `notify`. El `while` re-chequea la condición.

**Cómo se evitan *lost wakeups*:** `paused` se lee y escribe siempre dentro de `synchronized(pauseLock)`. Un hilo que aún no llegó al `wait()` verá `paused=true` cuando entre al bloque y esperará. No hay ventana donde `paused=true` antes de que el hilo entre al monitor.

**Por qué no hay busy-wait:** los hilos trabajadores están en `pauseLock.wait()` (suspendidos por el SO) hasta que `notifyAll()` los despierte. CPU = 0% durante la pausa.

#### Cómo ejecutar PrimeFinder

```bash
cd wait-notify-excercise_DUCU
mvn clean compile
mvn exec:java
```
#### Donde encontrar la solucion

[PrimeFinder](https://github.com/DUCU844/wait-notify-excercise_DUCU.git)

Cada 5 segundos el programa imprime cuántos primos encontró cada hilo y espera ENTER.

### Parte II — SnakeRace concurrente

#### Archivos modificados / creados

| Archivo | Tipo | Qué cambió |
|---|---|---|
| `concurrency/PauseBarrier.java` | **NUEVO** | Monitor compartido para pausar/reanudar todos los runners |
| `core/Snake.java` | modificado | Synchronize `head()` / `snapshot()` / `advance()`; agrega `index`, `markDead()`, `isDead()`, `deathTime()` |
| `concurrency/SnakeRunner.java` | modificado | Agrega pausa cooperativa con `PauseBarrier`; detección de muerte por colisión cuerpo-a-cuerpo |
| `ui/legacy/SnakeApp.java` | modificado | Pausa real (clock + barrier); estadísticas al pausar; Game Over; render serpientes muertas en gris |

---

#### Problema 1 — `Snake.body` no era thread-safe

**Código original:**
```java
private final Deque<Position> body = new ArrayDeque<>();   // sin protección
public Position head()                    { return body.peekFirst(); }
public Deque<Position> snapshot()         { return new ArrayDeque<>(body); }
public void advance(Position h, boolean g){ body.addFirst(h); ... }
```

`ArrayDeque` **no es thread-safe**. Tres hilos accedían concurrentemente:
- **SnakeRunner** → `advance()` (escribe body)
- **Board.step()** (dentro de SnakeRunner) → `head()` (lee body)
- **EDT / Swing paint** → `snapshot()` (itera body)

Resultado: `ConcurrentModificationException` o lecturas corruptas al pintar.

**Solución — synchronize las 3 regiones críticas sobre `this` (el Snake):**
```java
public synchronized Position head()    { return body.peekFirst(); }
public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }
public synchronized void advance(Position newHead, boolean grow) {
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) body.removeLast();
}
```

`turn()` y `direction()` NO se sincronizaron: acceden a `direction` que ya es `volatile` → visibilidad garantizada sin lock.

**Orden de locks — sin deadlock:**
```
SnakeRunner thread:  Board.step() → synchronized(board) → synchronized(snake)
EDT paint thread:    board.mice() → synchronized(board) [suelta] → snake.snapshot() → synchronized(snake)
```
El paint nunca sostiene el lock de Board cuando pide el de Snake → no hay inversión de orden → no hay deadlock.

---

#### Problema 2 — Pausa falsa (solo el repaint se detenía)

**Código original:**
```java
// SnakeApp.togglePause()
clock.pause();   // solo detiene el repaint — ¡las serpientes seguían moviéndose!
```

```java
// SnakeRunner.run() — sin ningún punto de pausa
while (!Thread.currentThread().isInterrupted()) {
    maybeTurn();
    board.step(snake);   // seguía ejecutando aunque el reloj estuviera pausado
    Thread.sleep(sleep);
}
```

**Solución — `PauseBarrier` (mismo patrón monitor que Parte I):**

```java
// PauseBarrier.java
public synchronized void waitIfPaused() throws InterruptedException {
    while (paused) wait();      // sin busy-wait; CPU = 0% mientras espera
}
public synchronized void pause()  { paused = true; }
public synchronized void resume() { paused = false; notifyAll(); }
```

```java
// SnakeRunner.run() — ahora con punto de pausa al inicio de cada iteración
while (!interrupted && !snake.isDead()) {
    barrier.waitIfPaused();     // si está pausado → wait() hasta notifyAll()
    maybeTurn();
    board.step(snake);
    ...
}
```

```java
// SnakeApp.togglePause() — ahora pausa AMBOS mecanismos
clock.pause();
barrier.pause();   // todos los runners entran a wait() al terminar su step() actual
```

**Por qué `while(paused)` y no `if(paused)`:** spurious wakeups — la JVM puede despertar un hilo sin que nadie llame `notify`. El `while` re-chequea la condición.

---

#### Problema 3 — Espera activa (busy-wait)

**Código original:** no había busy-wait explícito, pero la "pausa" del juego era inefectiva: los runners nunca dejaban de correr, consumiendo CPU innecesariamente aunque el juego estuviera "pausado".

**Solución:** `barrier.waitIfPaused()` llama a `Object.wait()` → el hilo queda suspendido por el SO, sin consumir CPU, hasta que `notifyAll()` lo despierte al reanudar.

---

#### Problema 4 — Colecciones no seguras en Board

`Board` usa `HashSet` y `HashMap` para `mice`, `obstacles`, `turbo`, `teleports`.

**No es un bug activo** porque `Board.step()` es `synchronized` sobre `board` → solo un hilo puede modificar esas colecciones a la vez. Los métodos `mice()`, `obstacles()`, etc. también son `synchronized` y retornan **copias defensivas** (`new HashSet<>(mice)`), así el paint no itera las colecciones internas.

No se cambió Board — la protección ya era correcta.

---

#### Problema 5 — No había condición de muerte para las serpientes

El requisito pide "peor serpiente (la que primero murió)". Sin muerte, no hay estadística posible.

**Solución — Colisión cuerpo-a-cuerpo como condición de muerte:**

Los obstáculos **siguen causando rebote** (regla original del juego). La muerte ocurre cuando la **cabeza de una serpiente entra en el cuerpo de otra**:

```java
// SnakeRunner.java — después de board.step()
default -> {
    if (hitsOtherSnake(snake.head())) {
        snake.markDead();   // graba isDead=true y deathTime=System.currentTimeMillis()
        return;             // el virtual thread de esta serpiente termina
    }
}
```

```java
private boolean hitsOtherSnake(Position head) {
    for (Snake other : allSnakes) {
        if (other == snake || other.isDead()) continue;
        if (other.snapshot().contains(head)) return true;   // snapshot() es synchronized
    }
    return false;
}
```

`other.snapshot()` adquiere el lock de esa Snake. En este punto no se sostiene el lock de Board ni de la propia serpiente → sin riesgo de deadlock.

---

#### Control de ejecución (UI)

| Acción | Antes | Después |
|---|---|---|
| Pausar | Solo detiene repaint | Pausa clock + barrier (runners entran a wait) |
| Reanudar | Solo reanuda repaint | `barrier.resume()` → `notifyAll()` → `clock.resume()` |
| Al pausar | Sin estadísticas | Dialog con serpiente viva más larga + primera en morir |
| Game Over | No existía | Detectado en tick; dialog con última serpiente sobreviviente |
| Serpiente muerta | No existía | Se renderiza en gris con una ✕ en la cabeza |

**Consistencia de las estadísticas al pausar:**

Al llamar `barrier.pause()`, los runners terminan su `board.step()` actual (atómico por el lock de Board) y luego entran a `wait()`. Las lecturas de `snapshot()` son `synchronized` sobre cada Snake, por lo que no hay tearing aunque un runner esté terminando su step. La diferencia máxima es de 1 paso por serpiente, aceptable para el laboratorio.

---

#### Robustez bajo carga (`-Dsnakes=20`)

Con 20 serpientes:
- **Sin `ConcurrentModificationException`**: `snapshot()` es `synchronized`, el paint nunca itera el body original.
- **Sin lecturas inconsistentes**: `board.step()` es `synchronized` sobre Board → solo 1 runner modifica mice/obstacles a la vez.
- **Sin deadlock**: orden de locks siempre Board → Snake; el paint nunca invierte ese orden.
- **Sin busy-wait**: runners en `wait()` mientras el juego está pausado.

---

#### Cómo ejecutar SnakeRace

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=20
```
### Implementación hecha por
- Adrian Ducuara

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
