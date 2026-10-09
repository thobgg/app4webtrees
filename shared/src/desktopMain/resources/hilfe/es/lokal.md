# Árbol genealógico en este PC

Sin servidor, wtWin crea el árbol genealógico en este ordenador. En segundo plano funciona un webtrees completo con PHP incluido, sin cambios respecto a la versión oficial. Solo es accesible en este PC, se inicia con el programa y se detiene con él. No ve ningún servidor ni ninguna contraseña; el programa inicia sesión por sí mismo.

## Crear

En el primer inicio, a la derecha: introduzca un nombre y haga clic en **Crear árbol genealógico** (vacío) o en **Importar desde un archivo GEDCOM …**. Para traer los datos de otro programa, exporte allí un archivo GEDCOM (`.ged`) y elíjalo aquí. Se importan personas, familias, eventos, fuentes y notas. El propio archivo GEDCOM no se modifica; una segunda importación crea otro árbol junto al primero y nunca sobrescribe.

El archivo puede estar en UTF-8 (con o sin marca de orden de bytes), UTF-16, ANSEL o ANSI; el juego de caracteres se detecta y se convierte como en la importación de webtrees. Los registros sueltos inutilizables (como ID duplicados) se omiten y se anotan en `import.log`; el resto se importa.

Las fotos no vienen con el GEDCOM. Añádalas en el programa o cópielas más tarde en la carpeta de medios (véase abajo) y vincúlelas en webtrees.

## Varios árboles

**Archivo › Árboles en este PC …** muestra todos los árboles con su número de personas. Allí puedes abrir uno, cambiarle el nombre (editar el título, luego la marca o Intro) o eliminarlo (nunca el último), crear otro árbol vacío o importar otro archivo GEDCOM. Los restos vacíos de importaciones fallidas anteriores – solo la persona de ejemplo «John Doe» de webtrees – se eliminan automáticamente al iniciar; un árbol que creaste vacío tú mismo se conserva.

## Dónde están los datos

| Sistema | Carpeta |
| - | - |
| Windows | `%LOCALAPPDATA%\app4webtrees` (escríbalo en la barra de direcciones del Explorador) |
| Linux | `~/.local/share/app4webtrees` |

Dentro están `webtrees/` con el programa y `webtrees/data/` con la base de datos (SQLite) y la carpeta de medios `media/`. El registro del servidor PHP es `php.log`. Lo que el propio wtWin detecta (conexiones interrumpidas, reintentos, reinicios del servidor) se anota en `wtwin.log`, al lado.

## Archivo

El árbol genealógico en este PC incluye el módulo **Sammlungen** (colecciones): las fotos y los documentos se guardan como carpetas en `data/media`, no tienen que estar asignados a personas y aparecen en **Fotos › Archivo** y también en webtrees en el navegador. Los escaneos de libros parroquiales del archivo se pueden asignar como fuente o como cita en el gestor de fuentes.

## Copia de seguridad

El programa no hace copias de seguridad automáticas. Hay dos maneras:

- Con wtWin cerrado, copie la carpeta `app4webtrees`, p. ej. a una memoria USB. Es la copia de seguridad completa, fotos incluidas.
- **Abrir webtrees en el navegador** y exportar el árbol como GEDCOM en el panel de control. Así se guardan los datos, no las imágenes.

## Todo lo de webtrees

**Archivo › Abrir webtrees en el navegador** muestra su webtrees en el navegador: panel de control, módulos, cambiar nombres, crear fuentes. El navegador pide su propio inicio de sesión. El nombre de usuario es su nombre de inicio de sesión en el PC; la contraseña se generó al azar al crear el árbol y se guardó en el archivo `zugang.properties` de la carpeta `app4webtrees` (véase arriba). Abra el archivo con un editor de texto y copie la contraseña. No la comparta: es el acceso de administrador a su árbol.

## Trasladar a un NAS o a un alojamiento web

Si la familia debe poder consultarlo o trabaja en dos ordenadores, el árbol se traslada a un servidor, p. ej. un Synology con nas4webtrees. Después usa wtWin como antes, solo que conectado.

1. En wtWin, **Abrir webtrees en el navegador** y allí **Panel de control › Árbol genealógico › Exportar** como GEDCOM.
2. Fotos: copie la carpeta `webtrees/data/media` (véase arriba) en `data/media` del webtrees del servidor.
3. En el servidor, cree un árbol nuevo e importe el GEDCOM.
4. En wtWin, **Archivo › Cerrar sesión**, **Otra dirección** e introduzca la dirección del servidor. O haga clic en **Connect with wtWin** en la página **App** del servidor.

El árbol del PC se conserva hasta que borre la carpeta.

## Si algo sale mal

Si no se puede crear el árbol, el programa muestra un mensaje con la ruta de `php.log`. Infórmelo en github.com/thobgg/app4webtrees/issues y adjunte el archivo y, al importar un archivo GEDCOM, también `import.log` de la misma carpeta (juego de caracteres, registros omitidos, motivo del fallo). **Ayuda › Acerca de wtWin** muestra si se encontraron PHP y webtrees.

**Persona de inicio:** si un árbol en este PC aún no tiene persona de inicio, el programa pregunta una vez al abrirlo «¿Con quién debe empezar el árbol?»: buscar a la persona y hacer clic. La elección pasa a ser la persona predeterminada del árbol; se cambia en Persona › Establecer como persona de inicio …
