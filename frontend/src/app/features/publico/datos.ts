/**
 * Contenido de EJEMPLO del sitio público: nombres, cifras, teléfonos y noticias son inventados.
 * Cuando el colegio tenga su información real, solo hay que cambiar este archivo.
 */
export const COLEGIO = {
  nombre: 'Colegio Horizonte del Valle',
  lema: 'Aprender hoy, liderar mañana',
  direccion: 'Av. Los Pinos 456, Urb. Valle Verde',
  ciudad: 'Lima, Perú',
  telefono: '(01) 555-0123',
  whatsapp: '+51 900 555 012',
  correo: 'contacto@horizonte-ejemplo.edu.pe',
  admisionCorreo: 'admision@horizonte-ejemplo.edu.pe',
  horarioAtencion: 'Lunes a viernes de 8:00 a 16:00 · Sábados de 9:00 a 12:00 (solo admisión)'
};

export const CIFRAS = [
  { valor: 25, sufijo: '', etiqueta: 'años de trayectoria' },
  { valor: 1240, sufijo: '', etiqueta: 'estudiantes' },
  { valor: 68, sufijo: '', etiqueta: 'docentes titulados' },
  { valor: 96, sufijo: ' %', etiqueta: 'ingresa a la universidad' }
];

export interface Nivel {
  id: 'inicial' | 'primaria' | 'secundaria';
  nombre: string;
  edades: string;
  resumen: string;
  horario: string;
  grados: string;
  pension: number;
  matricula: number;
  destacados: string[];
  talleres: string[];
  color: string;
}

export const NIVELES: Nivel[] = [
  {
    id: 'inicial', nombre: 'Inicial', edades: '3 a 5 años', color: '#f59e0b',
    resumen: 'Un primer colegio cálido donde jugar es la manera de aprender: lenguaje, números, arte y convivencia.',
    horario: '8:00 a 13:00', grados: '3, 4 y 5 años', pension: 380, matricula: 400,
    destacados: ['Máximo 20 niños por aula con docente y auxiliar', 'Psicomotricidad y música todas las semanas', 'Huerto escolar y juego libre al aire libre', 'Informe de progreso trimestral con reunión individual'],
    talleres: ['Música', 'Danza', 'Cuentacuentos', 'Huerto']
  },
  {
    id: 'primaria', nombre: 'Primaria', edades: '6 a 11 años', color: '#2f5bea',
    resumen: 'Bases sólidas en comprensión lectora y matemática, con proyectos que conectan lo aprendido con la vida real.',
    horario: '7:45 a 14:30', grados: '1.° a 6.° grado', pension: 450, matricula: 450,
    destacados: ['Inglés desde 1.° con 6 horas semanales', 'Matemática con método de resolución de problemas', 'Plan lector con biblioteca de aula', 'Tutoría y seguimiento personalizado'],
    talleres: ['Ajedrez', 'Robótica inicial', 'Fútbol y vóley', 'Pintura', 'Teatro']
  },
  {
    id: 'secundaria', nombre: 'Secundaria', edades: '12 a 16 años', color: '#7c3aed',
    resumen: 'Pensamiento crítico, ciencias y orientación vocacional para llegar preparados a la educación superior.',
    horario: '7:45 a 15:30', grados: '1.° a 5.° año', pension: 520, matricula: 500,
    destacados: ['Laboratorios de ciencias y de cómputo', 'Preparación preuniversitaria desde 4.°', 'Orientación vocacional con psicólogos', 'Intercambios y certificación de inglés'],
    talleres: ['Robótica', 'Debate', 'Banda escolar', 'Básquet', 'Periodismo escolar']
  }
];

export const HITOS = [
  { anio: '2001', titulo: 'Nace el colegio', texto: 'Abrimos con 3 aulas y 64 estudiantes de inicial y primaria.' },
  { anio: '2008', titulo: 'Llega la secundaria', texto: 'Se inaugura el pabellón de secundaria y los primeros laboratorios.' },
  { anio: '2014', titulo: 'Primera promoción universitaria', texto: 'El 94 % de nuestra primera promoción ingresa a la universidad.' },
  { anio: '2019', titulo: 'Aulas digitales', texto: 'Cada aula incorpora pizarras interactivas y conectividad total.' },
  { anio: '2023', titulo: 'Campus sostenible', texto: 'Paneles solares, huerto y programa de reciclaje escolar.' },
  { anio: '2026', titulo: 'Plataforma EduSpace', texto: 'Familias, docentes y estudiantes gestionan todo en un solo portal.' }
];

export const VALORES = [
  { titulo: 'Respeto', texto: 'Convivimos reconociendo al otro y sus ideas.' },
  { titulo: 'Excelencia', texto: 'Damos lo mejor en cada tarea, sin atajos.' },
  { titulo: 'Responsabilidad', texto: 'Cumplimos y respondemos por nuestras decisiones.' },
  { titulo: 'Solidaridad', texto: 'Aprendemos sirviendo a la comunidad.' }
];

export const EQUIPO = [
  { nombre: 'Dra. Carmen Salazar Ruiz', cargo: 'Directora general', iniciales: 'CS' },
  { nombre: 'Mg. Luis Paredes Vega', cargo: 'Subdirector académico', iniciales: 'LP' },
  { nombre: 'Lic. Rosa Quispe Mamani', cargo: 'Coordinadora de primaria', iniciales: 'RQ' },
  { nombre: 'Ps. Daniel Rojas Cano', cargo: 'Jefe de bienestar estudiantil', iniciales: 'DR' }
];

export interface Instalacion { nombre: string; tipo: 'Aprendizaje' | 'Deporte' | 'Arte' | 'Bienestar'; detalle: string; color: string }
export const INSTALACIONES: Instalacion[] = [
  { nombre: 'Laboratorio de ciencias', tipo: 'Aprendizaje', detalle: 'Equipado para química, física y biología.', color: '#2f5bea' },
  { nombre: 'Aula de robótica', tipo: 'Aprendizaje', detalle: 'Kits de programación y electrónica.', color: '#0891b2' },
  { nombre: 'Biblioteca', tipo: 'Aprendizaje', detalle: '12 000 libros y sala de estudio silenciosa.', color: '#4f46e5' },
  { nombre: 'Losa deportiva techada', tipo: 'Deporte', detalle: 'Fútbol, vóley y básquet todo el año.', color: '#16a34a' },
  { nombre: 'Piscina semiolímpica', tipo: 'Deporte', detalle: 'Clases de natación desde primaria.', color: '#0ea5e9' },
  { nombre: 'Auditorio', tipo: 'Arte', detalle: '300 butacas para teatro y música.', color: '#db2777' },
  { nombre: 'Taller de arte', tipo: 'Arte', detalle: 'Pintura, cerámica y arte digital.', color: '#f97316' },
  { nombre: 'Tópico y psicología', tipo: 'Bienestar', detalle: 'Atención de salud y orientación.', color: '#dc2626' }
];

export type CategoriaNoticia = 'Académico' | 'Deportes' | 'Cultura' | 'Comunidad';
export interface Noticia {
  id: number; titulo: string; categoria: CategoriaNoticia; fecha: string; resumen: string; cuerpo: string[];
}
export const NOTICIAS: Noticia[] = [
  { id: 1, titulo: 'Abren las inscripciones de admisión 2027', categoria: 'Comunidad', fecha: '2026-10-20', resumen: 'Ya puedes separar una vacante y agendar la visita guiada al campus.', cuerpo: ['Desde este mes recibimos solicitudes para inicial, primaria y secundaria. Las vacantes son limitadas por aula para cuidar la atención personalizada.', 'Agenda tu visita desde la sección Admisión: conocerás las aulas, los laboratorios y hablarás con nuestros coordinadores.'] },
  { id: 2, titulo: 'Equipo de robótica clasifica a la final regional', categoria: 'Académico', fecha: '2026-10-14', resumen: 'Cuatro estudiantes de 3.° de secundaria competirán con un robot clasificador de residuos.', cuerpo: ['El equipo «Horizonte Bots» superó a 32 colegios en la fase provincial con un prototipo que separa plástico, vidrio y papel.', 'La final regional será el 14 de noviembre. Los entrenamientos son de lunes a jueves después de clases.'] },
  { id: 3, titulo: 'Campeonato interno de vóley: ya hay finalistas', categoria: 'Deportes', fecha: '2026-10-09', resumen: '5.° A y 4.° B jugarán la gran final este viernes en la losa techada.', cuerpo: ['Después de tres semanas de partidos, quedaron dos equipos invictos. La final será a las 3:00 p. m. y está abierta para las familias.'] },
  { id: 4, titulo: 'Festival de talentos «Voces del Valle»', categoria: 'Cultura', fecha: '2026-10-02', resumen: 'Más de 60 estudiantes mostraron música, danza y teatro ante un auditorio lleno.', cuerpo: ['Fue una tarde de aplausos. Agradecemos a las familias por acompañar y a los docentes de arte por la preparación.'] },
  { id: 5, titulo: 'Semana de la lectura: libros en cada rincón', categoria: 'Académico', fecha: '2026-09-25', resumen: 'Intercambio de libros, cuentacuentos y la visita de un autor local.', cuerpo: ['Durante cinco días, cada aula montó su «rincón lector». El jueves, el escritor invitado conversó con los estudiantes de primaria.'] },
  { id: 6, titulo: 'Campaña de reciclaje: 1,2 toneladas recuperadas', categoria: 'Comunidad', fecha: '2026-09-18', resumen: 'Las familias y los estudiantes superaron la meta del año.', cuerpo: ['Lo recaudado con el material reciclado se destinará a renovar la biblioteca de inicial.'] },
  { id: 7, titulo: 'Obra de teatro: «El viaje de Inti»', categoria: 'Cultura', fecha: '2026-09-05', resumen: 'Estudiantes de 5.° y 6.° de primaria estrenan su versión de un cuento andino.', cuerpo: ['Funciones el 12 y 13 de noviembre en el auditorio. Entrada libre con inscripción previa.'] },
  { id: 8, titulo: 'Olimpiada de matemática: 11 medallas', categoria: 'Académico', fecha: '2026-08-28', resumen: 'Nuestros estudiantes obtuvieron 4 de oro, 3 de plata y 4 de bronce.', cuerpo: ['Felicitamos a los participantes y a los docentes del área por su preparación durante todo el año.'] }
];

export interface Evento { fecha: string; titulo: string; lugar: string; hora: string }
export const EVENTOS: Evento[] = [
  { fecha: '2026-11-07', titulo: 'Visita guiada al campus (admisión)', lugar: 'Recepción principal', hora: '10:00' },
  { fecha: '2026-11-12', titulo: 'Obra «El viaje de Inti»', lugar: 'Auditorio', hora: '17:00' },
  { fecha: '2026-11-14', titulo: 'Final regional de robótica', lugar: 'Coliseo municipal', hora: '09:00' },
  { fecha: '2026-11-21', titulo: 'Examen de admisión (1.ª fecha)', lugar: 'Pabellón B', hora: '09:00' },
  { fecha: '2026-12-05', titulo: 'Clausura del año escolar', lugar: 'Patio central', hora: '16:00' },
  { fecha: '2026-12-12', titulo: 'Examen de admisión (2.ª fecha)', lugar: 'Pabellón B', hora: '09:00' }
];

export const TESTIMONIOS = [
  { nombre: 'Marcela Torres', rol: 'Madre de familia · 3.° de primaria', texto: 'Mi hija llegó tímida y hoy lidera su grupo de teatro. Los profesores realmente conocen a cada niño.' },
  { nombre: 'Jorge Aliaga', rol: 'Padre de familia · 2.° de secundaria', texto: 'Desde el portal veo notas, tareas y pagos en un solo lugar. Ahorramos mucho tiempo.' },
  { nombre: 'Valeria Núñez', rol: 'Exalumna · Promoción 2020', texto: 'La preparación y la orientación vocacional me ayudaron a ingresar a mi primera opción.' }
];

export const PASOS_ADMISION = [
  { titulo: 'Solicita información', texto: 'Escríbenos o llena la pre-inscripción. Te responderemos en 24 horas hábiles.' },
  { titulo: 'Visita el colegio', texto: 'Recorre el campus con nuestro equipo y resuelve todas tus dudas.' },
  { titulo: 'Evaluación del estudiante', texto: 'Una entrevista amable y una prueba de diagnóstico según el grado.' },
  { titulo: 'Entrega de documentos', texto: 'Presenta los requisitos en secretaría (puedes adelantarlos escaneados).' },
  { titulo: 'Matrícula', texto: 'Con la vacante confirmada, pagas la matrícula y recibes tu usuario del portal.' }
];

export const REQUISITOS = [
  'Copia del DNI del estudiante y de los padres o apoderados',
  'Partida de nacimiento original',
  'Ficha SIAGIE o constancia de matrícula del colegio anterior',
  'Certificado de estudios (de 2.° de primaria en adelante)',
  'Carné de vacunación al día',
  'Dos fotos tamaño carné'
];

export const FAQ = [
  { p: '¿Cuándo es el proceso de admisión?', r: 'Recibimos solicitudes de octubre a febrero. Las evaluaciones se programan en noviembre, diciembre y enero según vacantes.' },
  { p: '¿Se puede pagar la pensión en 10 cuotas?', r: 'Sí. La pensión se divide en 10 cuotas de marzo a diciembre. El pago adelantado de todo el año tiene descuento.' },
  { p: '¿Hay descuento por hermanos?', r: 'El segundo hermano tiene 10 % y desde el tercero 15 % sobre la pensión.' },
  { p: '¿Cómo me entero de las notas y tareas?', r: 'Con tu usuario del portal EduSpace revisas notas, asistencia, tareas, horarios y pagos desde el celular o la computadora.' },
  { p: '¿Cuentan con movilidad escolar?', r: 'Trabajamos con empresas de transporte autorizadas por zonas. Te damos la lista al matricular.' },
  { p: '¿Qué pasa si mi hijo viene de otro colegio a mitad de año?', r: 'Evaluamos cada caso con el coordinador del nivel y, si hay vacante, lo recibimos con un plan de nivelación.' },
  { p: '¿Ofrecen alimentación?', r: 'Contamos con quiosco saludable y servicio de almuerzo opcional con menú semanal publicado.' },
  { p: '¿Hay talleres después de clases?', r: 'Sí: deportes, arte, robótica y música, de lunes a jueves de 3:30 a 5:00 p. m. (costo aparte).' }
];
