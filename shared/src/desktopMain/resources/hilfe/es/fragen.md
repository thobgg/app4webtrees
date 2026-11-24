# Preguntas, errores, privacidad

## Informar de preguntas y errores

![](desktop-hilfe.jpg)


wtWin, wtTux, wtMac y wtAnd no son productos oficiales de webtrees. No pregunte en el foro de webtrees; abra una incidencia en **github.com/thobgg/app4webtrees/issues**. Resulta útil indicar: la versión de **Ayuda › Acerca de wtWin** (con número de compilación), Windows o Linux, y qué estaba haciendo cuando ocurrió. Para el árbol genealógico en este PC, también el archivo `php.log` (véase [Árbol genealógico en este PC](hilfe:lokal)).

Las versiones nuevas aparecen en **github.com/thobgg/app4webtrees/releases**. El archivo de Windows no está firmado, por lo que en el primer inicio Windows advierte de un editor desconocido («Más información» y luego «Ejecutar de todas formas»).

## Lo que guarda el programa

- Dirección del servidor, nombre de usuario y la cookie de sesión, **nunca la contraseña de webtrees**. Solo los datos de una protección de directorio (si se introducen) quedan en el PC: deben acompañar cada solicitud.
- A partir de api4webtrees 1.30, un token de dispositivo para volver a conectarse sin preguntar. Cambia con cada uso; así un token robado deja de servir y bloquea el dispositivo en el servidor.
- Sus ajustes (disposición, aspecto, ajustes de gráficos y listas, reglas de color, lista de marcas de la comprobación) en este PC.
- Para el árbol genealógico en este PC: el árbol entero en la carpeta `app4webtrees`, incluido el acceso al webtrees local.
- Sin analíticas, sin publicidad, sin cesión de datos.

## Privacidad en el árbol

El programa inicia sesión con su cuenta de webtrees; cada solicitud se ejecuta como ese usuario. Se aplican las mismas reglas que en el sitio web: las personas vivas, los registros bloqueados y los árboles privados solo son visibles si webtrees lo permite. Las salidas (gráficos, listas, libros, PDF) contienen solo lo que usted puede ver.

## Sin cifrar en la red doméstica

Una dirección `http://` solo se acepta en la red doméstica (direcciones privadas como 192.168…, nombres como `diskstation` o `.local`). La conexión no está cifrada, lo cual está bien en casa. Para acceder al árbol desde fuera, el servidor necesita HTTPS; nas4webtrees ofrece las instrucciones.

## Móvil y tableta

Para Android existe **wtAnd**, de la misma familia y con los mismos datos: árbol, fotos, edición, aniversarios. Un código QR en la página **App** de webtrees conecta el móvil sin escribir nada. wtAnd necesita un servidor; el árbol genealógico en este PC solo es accesible en el PC.

## Comprobación de actualizaciones

Una vez al día, al iniciar, el programa pide a `wtwin.bgg-home.de` el número de versión actual: un pequeño archivo de texto, nada más. No se envía nada salvo esa petición: ni árbol, ni identificador, ni uso. Si hay una versión más reciente, la barra de estado muestra «La versión 1.52 está disponible – Descargar»; un clic abre la página de descarga, × omite esa versión. Sin red no pasa nada. Desactivar: **Ayuda › Buscar actualizaciones**; comprobar a mano: **Ayuda › Buscar actualizaciones ahora …**. Una actualización solo se instala encima; ajustes, sesión y el árbol en este PC se mantienen.

## Licencia

wtWin se distribuye bajo la licencia GPL-3, como webtrees. El árbol genealógico en este PC contiene webtrees sin cambios respecto a la versión oficial (webtrees.net).

## Manual completo

Con imágenes y notas actualizadas: **wtwin.bgg-home.de** (botón al pie de esta ventana).
