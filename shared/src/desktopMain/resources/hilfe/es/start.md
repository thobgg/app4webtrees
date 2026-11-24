# Primeros pasos

wtWin (wtTux en Linux) muestra y edita un árbol genealógico de webtrees como un programa clásico de genealogía para escritorio: barra de menús, navegador, ficha personal, gráficos, listas y libros. Los datos están en un servidor webtrees (NAS o alojamiento web) o directamente en este PC.

![](desktop-start.jpg)


En el primer inicio, ambas opciones aparecen una al lado de la otra.

## Conectar con webtrees

Requisito: webtrees 2.2 con el módulo **api4webtrees**. Quien administra el servidor instala el módulo; el paquete nas4webtrees ya lo incluye.

**Lo más sencillo, sin escribir nada:** inicie sesión en webtrees en el navegador, abra el menú **App** y haga clic en **Connect with wtWin** (conectar con wtWin). El programa recoge el enlace de conexión (del portapapeles o directamente del navegador), pregunta una vez y abre el árbol. Sin dirección ni contraseña. El enlace es válido durante diez minutos y una sola vez.

**A mano:**

1. Introduzca la dirección de su sitio webtrees tal como aparece en el navegador. El programa recorta el resto por sí mismo. Si hay una dirección adecuada en el portapapeles, se propone.
2. Haga clic en **Conectar**.
3. Inicie sesión con el nombre de usuario (o el correo electrónico) y la contraseña. **Ver sin iniciar sesión** muestra solo lo que ven los visitantes del sitio web.

> **Protección de directorio:** si el navegador muestra antes de webtrees una pequeña ventana de acceso del servidor web (.htaccess), despliegue en la pantalla de dirección «¿El servidor pide usuario y contraseña antes de webtrees?» e introduzca ahí esos datos. El programa los envía con cada solicitud a este servidor; después inicia sesión como de costumbre. Si el servidor exige ese acceso, los campos se despliegan solos.

> **Seguir conectado:** muchos servidores eliminan la sesión de webtrees tras una pausa de unos 24 minutos. A partir de api4webtrees 1.30, el programa recibe un token de dispositivo al iniciar sesión y lo usa para volver a conectarse sin preguntar; solo pide de nuevo la contraseña si el token lleva 90 días sin usarse, si la contraseña ha cambiado o si el dispositivo se ha desconectado. Los dispositivos conectados aparecen en webtrees en el menú **App**, donde cada uno puede desconectarse. **Cerrar sesión** también desconecta el dispositivo.

Se inicia sesión con la cuenta normal de webtrees. Se aplican los mismos derechos que en el sitio web: lo que no puede ver allí, tampoco lo ve aquí; lo que puede editar allí, también puede editarlo aquí.

> Las direcciones sin cifrar (`http://`) solo se aceptan en la red doméstica, p. ej. `http://192.168.178.73:8095`. Fuera de casa, el servidor necesita HTTPS.

## Árbol genealógico en este PC

A la derecha de la pantalla de inicio: introduzca un nombre y haga clic en **Crear árbol genealógico**, o en **Importar desde un archivo GEDCOM …** para traer los datos de otro programa. A partir de ese momento trabaja sin servidor, sin contraseña y sin internet. Consulte [Árbol genealógico en este PC](hilfe:lokal).

## Cambiar de árbol o de servidor

- Si el servidor tiene varios árboles, cambie en **Archivo › Cambiar de árbol genealógico**.
- A otro servidor: **Archivo › Cerrar sesión** y luego **Otra dirección**. O haga clic en **Connect with wtWin** en la página **App** del otro servidor; esto también funciona con la sesión iniciada.

## Idioma

El programa sigue el idioma del sistema (alemán, inglés, francés, neerlandés o español; si no, inglés). Elija un idioma fijo en **Ver › Idioma**. Las etiquetas que vienen del servidor (nombres de eventos, parentescos) aparecen en el idioma del programa.

## Siguiente

- [La ventana principal](hilfe:hauptfenster): navegador, barra de herramientas, vista
- [Ficha personal y edición](hilfe:person)
- [Gráficos](hilfe:tafeln), [Listas](hilfe:listen), [Libros](hilfe:buecher)
- [Atajos de teclado](hilfe:tasten)
