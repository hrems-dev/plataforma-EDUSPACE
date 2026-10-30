import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GradoOferta, InicioMatricula, OfertaMatricula, ResumenMatricula } from '../../core/models/matricula-publica.model';
import { MatriculaPublicaService } from '../../core/services/matricula-publica.service';
import { ModalComponent } from '../../shared/components/modal/modal.component';

type Paso = 'grado' | 'datos' | 'pago' | 'listo';

const CLAVE = 'eduspace_matricula_en_linea';
const PARENTESCOS = [['MADRE', 'Madre'], ['PADRE', 'Padre'], ['TUTOR', 'Tutor(a)'], ['ABUELO', 'Abuelo'], ['ABUELA', 'Abuela'], ['TIO', 'Tío'], ['TIA', 'Tía'], ['OTRO', 'Otro']];

/** Un DNI tiene 8 números; los otros documentos admiten letras y números. */
const documentoValido = (control: AbstractControl): ValidationErrors | null => {
  const tipo = control.parent?.get('tipoDocumento')?.value;
  const v = String(control.value ?? '').trim();
  if (!v) return null;
  if (tipo === 'DNI') return /^\d{8}$/.test(v) ? null : { dni: true };
  return /^[A-Za-z0-9]{6,20}$/.test(v) ? null : { documento: true };
};

/**
 * Matrícula en línea del sitio público: elige el grado, completa los datos del alumno y del apoderado, reserva el cupo y paga
 * con una pasarela de DEMOSTRACIÓN. Al pagar, la matrícula queda en revisión; cuando la administración la aprueba se crean las cuentas y se envía por correo el acceso y la constancia.
 */
@Component({
  selector: 'app-matricula-en-linea',
  imports: [ReactiveFormsModule, DecimalPipe, RouterLink, ModalComponent],
  encapsulation: ViewEncapsulation.None,
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip pub-chip-oro">Matrícula {{ oferta()?.anio ?? '' }}</span>
      <h1>Matricúlate en línea</h1>
      <p>Reserva la vacante y paga la matrícula en línea. Cuando el colegio la apruebe, recibes por correo tu usuario, tu clave, la constancia y el horario.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap mel">
      <ol class="mel-pasos" aria-label="Avance">
        @for (p of pasos; track p.id; let i = $index) {
          <li [class.on]="paso() === p.id" [class.hecho]="indice() > i" [attr.aria-current]="paso() === p.id ? 'step' : null">
            <span class="mel-n">{{ indice() > i ? '✓' : i + 1 }}</span><span>{{ p.titulo }}</span>
          </li>
        }
      </ol>

      @if (cargando()) {
        <div class="pub-tarjeta">Cargando la oferta de vacantes…</div>
      } @else if (errorCarga()) {
        <div class="pub-tarjeta mel-error" role="alert">{{ errorCarga() }}</div>
      } @else if (oferta() && !oferta()!.abierta) {
        <div class="pub-tarjeta"><h2>Las matrículas en línea están cerradas por ahora</h2><p>{{ oferta()!.mensaje }}</p><a class="pub-btn pub-btn-primario" routerLink="/contacto">Escríbenos</a></div>
      } @else {

        <!-- 1. Grado -->
        @if (paso() === 'grado') {
          <h2 class="mel-h">¿En qué grado se matricula?</h2>
          <div class="pub-grupo" role="tablist" aria-label="Nivel">
            @for (n of oferta()!.niveles; track n.id) {
              <button type="button" role="tab" [class.activo]="nivelId() === n.id" [attr.aria-selected]="nivelId() === n.id" (click)="nivelId.set(n.id)">{{ n.nombre }}</button>
            }
          </div>
          <div class="mel-grados">
            @for (g of gradosDelNivel(); track g.id) {
              <button type="button" class="mel-grado" [class.sel]="gradoId() === g.id" [disabled]="g.vacantes === 0 || g.matricula === null" (click)="elegirGrado(g)">
                <strong>{{ g.nombre }}</strong>
                <span class="mel-vac" [class.poco]="g.vacantes > 0 && g.vacantes <= 5">{{ g.vacantes === 0 ? 'Sin vacantes' : g.vacantes + ' vacante(s)' }}</span>
                @if (g.matricula !== null) {
                  <span>Matrícula <b>S/ {{ g.matricula | number: '1.2-2' }}</b></span>
                  @if (g.pension) { <span>Pensión <b>S/ {{ g.pension.mensual | number: '1.2-2' }}</b> × {{ g.pension.cuotas }}</span> }
                } @else { <span class="muted">Sin tarifa publicada</span> }
              </button>
            }
          </div>
          <div class="mel-acc"><button type="button" class="pub-btn pub-btn-primario" [disabled]="!gradoId()" (click)="ir('datos')">Continuar</button></div>
        }

        <!-- 2. Datos -->
        @if (paso() === 'datos') {
          <form [formGroup]="form" (ngSubmit)="ir('pago')" novalidate>
            <div class="pub-dos mel-dos">
              <fieldset class="pub-tarjeta" formGroupName="alumno">
                <legend>Datos del alumno</legend>
                <div class="pub-campo"><label for="a-tipo">Documento</label>
                  <div class="mel-fila">
                    <select id="a-tipo" class="pub-input mel-corto" formControlName="tipoDocumento"><option value="DNI">DNI</option><option value="CE">CE</option><option value="PASAPORTE">Pasaporte</option></select>
                    <input id="a-doc" class="pub-input" formControlName="numeroDocumento" inputmode="numeric" autocomplete="off" placeholder="Número" aria-label="Número de documento del alumno" />
                  </div>
                  @if (malo('alumno.numeroDocumento')) { <span class="mel-msg">{{ mensajeDoc('alumno') }}</span> }</div>
                <div class="pub-campo"><label for="a-nom">Nombres</label><input id="a-nom" class="pub-input" formControlName="nombres" autocomplete="off" />
                  @if (malo('alumno.nombres')) { <span class="mel-msg">Escribe los nombres.</span> }</div>
                <div class="mel-fila">
                  <div class="pub-campo"><label for="a-ap">Apellido paterno</label><input id="a-ap" class="pub-input" formControlName="apellidoPaterno" autocomplete="off" />
                    @if (malo('alumno.apellidoPaterno')) { <span class="mel-msg">Escribe el apellido.</span> }</div>
                  <div class="pub-campo"><label for="a-am">Apellido materno</label><input id="a-am" class="pub-input" formControlName="apellidoMaterno" autocomplete="off" /></div>
                </div>
                <div class="mel-fila">
                  <div class="pub-campo"><label for="a-fn">Fecha de nacimiento</label><input id="a-fn" type="date" class="pub-input" formControlName="fechaNacimiento" [max]="hoy" />
                    @if (malo('alumno.fechaNacimiento')) { <span class="mel-msg">Indica una fecha válida.</span> }</div>
                  <div class="pub-campo"><label for="a-sx">Sexo</label>
                    <select id="a-sx" class="pub-input" formControlName="sexo"><option value="F">Femenino</option><option value="M">Masculino</option></select></div>
                </div>
              </fieldset>

              <fieldset class="pub-tarjeta" formGroupName="apoderado">
                <legend>Datos del apoderado</legend>
                <div class="pub-campo"><label for="p-tipo">Documento</label>
                  <div class="mel-fila">
                    <select id="p-tipo" class="pub-input mel-corto" formControlName="tipoDocumento"><option value="DNI">DNI</option><option value="CE">CE</option><option value="PASAPORTE">Pasaporte</option></select>
                    <input id="p-doc" class="pub-input" formControlName="numeroDocumento" inputmode="numeric" autocomplete="off" placeholder="Número" aria-label="Número de documento del apoderado" />
                  </div>
                  @if (malo('apoderado.numeroDocumento')) { <span class="mel-msg">{{ mensajeDoc('apoderado') }}</span> }</div>
                <div class="pub-campo"><label for="p-nom">Nombres</label><input id="p-nom" class="pub-input" formControlName="nombres" autocomplete="given-name" />
                  @if (malo('apoderado.nombres')) { <span class="mel-msg">Escribe los nombres.</span> }</div>
                <div class="pub-campo"><label for="p-ape">Apellidos</label><input id="p-ape" class="pub-input" formControlName="apellidos" autocomplete="family-name" />
                  @if (malo('apoderado.apellidos')) { <span class="mel-msg">Escribe los apellidos.</span> }</div>
                <div class="mel-fila">
                  <div class="pub-campo"><label for="p-tel">Teléfono</label><input id="p-tel" class="pub-input" formControlName="telefono" inputmode="tel" autocomplete="tel" />
                    @if (malo('apoderado.telefono')) { <span class="mel-msg">Escribe un teléfono válido.</span> }</div>
                  <div class="pub-campo"><label for="p-par">Parentesco</label>
                    <select id="p-par" class="pub-input" formControlName="parentesco">@for (p of parentescos; track p[0]) { <option [value]="p[0]">{{ p[1] }}</option> }</select></div>
                </div>
                <div class="pub-campo"><label for="p-mail">Correo (para enviarte la constancia)</label>
                  <input id="p-mail" type="email" class="pub-input" formControlName="email" autocomplete="email" placeholder="opcional" />
                  @if (malo('apoderado.email')) { <span class="mel-msg">El correo no es válido.</span> }
                  <small class="muted">Si no lo escribes, podrás descargar la constancia al terminar.</small></div>
              </fieldset>
            </div>
            <!-- campo trampa: las personas no lo ven -->
            <div class="mel-trampa" aria-hidden="true"><label>No lo llenes <input type="text" tabindex="-1" autocomplete="off" formControlName="sitio" /></label></div>
            <div class="mel-acc">
              <button type="button" class="pub-btn pub-btn-borde" (click)="ir('grado')">Atrás</button>
              <button type="submit" class="pub-btn pub-btn-primario">Revisar y pagar</button>
            </div>
          </form>
        }

        <!-- 3. Resumen y pago -->
        @if (paso() === 'pago') {
          <h2 class="mel-h">Revisa y paga la matrícula</h2>
          <div class="pub-dos mel-dos">
            <div class="pub-tarjeta">
              <h3>Resumen</h3>
              <dl class="mel-dl">
                <div><dt>Grado</dt><dd>{{ gradoActual()?.nombre }}</dd></div>
                <div><dt>Alumno</dt><dd>{{ f.alumno.value.nombres }} {{ f.alumno.value.apellidoPaterno }} {{ f.alumno.value.apellidoMaterno }}</dd></div>
                <div><dt>Documento</dt><dd>{{ f.alumno.value.tipoDocumento }} {{ f.alumno.value.numeroDocumento }}</dd></div>
                <div><dt>Apoderado</dt><dd>{{ f.apoderado.value.nombres }} {{ f.apoderado.value.apellidos }}</dd></div>
                <div><dt>Correo</dt><dd>{{ f.apoderado.value.email || 'No indicado (descargarás la constancia aquí)' }}</dd></div>
              </dl>
              @if (inicio(); as i) { <div class="pub-ok">Tu vacante está reservada en la sección <b>{{ i.seccion }}</b> ({{ i.turno }}) hasta el {{ i.vencePago }}.</div> }
            </div>
            <div class="pub-tarjeta">
              <h3>Costos</h3>
              <div class="mel-cuenta"><span>Matrícula (se paga ahora)</span><b>S/ {{ (inicio()?.montoMatricula ?? gradoActual()?.matricula ?? 0) | number: '1.2-2' }}</b></div>
              @if (gradoActual()?.pension; as p) {
                <div class="mel-cuenta"><span>Pensión: {{ p.cuotas }} cuotas de</span><b>S/ {{ p.mensual | number: '1.2-2' }}</b></div>
                <div class="mel-cuenta"><span>Total de pensiones</span><b>S/ {{ p.total | number: '1.2-2' }}</b></div>
              }
              <p class="muted" style="font-size: .85rem">Las pensiones se pagan después en el portal de familias o en caja. Se generan cuando se aprueba la matrícula.</p>
              <label class="pub-check"><input type="checkbox" [checked]="acepto()" (change)="acepto.set($any($event.target).checked)" /><span>Acepto el tratamiento de mis datos personales y los del alumno para el proceso de matrícula.</span></label>
              @if (error()) { <div class="mel-error" role="alert">{{ error() }}</div> }
              <div class="mel-acc" style="justify-content: space-between">
                <button type="button" class="pub-btn pub-btn-borde" [disabled]="trabajando()" (click)="ir('datos')">Atrás</button>
                @if (inicio()) { <button type="button" class="pub-btn pub-btn-borde" [disabled]="trabajando()" (click)="nuevaMatricula()">Empezar de nuevo</button> }
                <button type="button" class="pub-btn pub-btn-oro pub-btn-grande" [disabled]="!acepto() || trabajando()" (click)="reservarYPagar()">{{ trabajando() ? 'Reservando…' : 'Pagar la matrícula' }}</button>
              </div>
            </div>
          </div>
        }

        <!-- 4. Listo -->
        @if (paso() === 'listo' && resumen(); as r) {
         @if (r.estado === 'PAGADA') {
          <div class="mel-exito" role="status">
            <span class="mel-check" aria-hidden="true">✓</span>
            <div><h2>¡Pago recibido! Tu matrícula está en revisión</h2><p>{{ r.alumno }} · {{ r.grado }} · sección {{ r.seccion }} ({{ r.turno }}) · código <b>{{ r.codigo }}</b></p></div>
          </div>
          <div class="pub-dos mel-dos">
            <div class="pub-tarjeta">
              <h3>¿Qué sigue?</h3>
              <ol class="mel-sigue">
                <li class="hecho">Reservaste la vacante y pagaste la matrícula.</li>
                <li class="ahora">La administración del colegio revisa y aprueba la matrícula.</li>
                <li>Te enviamos por correo tu <b>usuario y clave</b>, la constancia (PDF), el horario y las pensiones.</li>
              </ol>
              @if (r.correo === 'ENVIADO') { <div class="pub-ok">Te escribimos a <b>{{ r.correoDestino }}</b> confirmando el pago. El siguiente correo llegará cuando se apruebe.</div> }
              @else { <div class="mel-aviso">No dejaste un correo (o no pudimos enviarlo). Guarda el código <b>{{ r.codigo }}</b> y consulta el estado aquí o en secretaría.</div> }
              <div class="mel-acc">
                <button type="button" class="pub-btn pub-btn-primario" [disabled]="actualizando()" (click)="actualizarEstado()">{{ actualizando() ? 'Consultando…' : 'Consultar estado' }}</button>
                <button type="button" class="pub-btn pub-btn-borde" (click)="nuevaMatricula()">Hacer otra matrícula</button>
              </div>
            </div>
            <div class="pub-tarjeta">
              <h3>Pago de la matrícula</h3>
              <div class="mel-cuenta"><span>Monto pagado</span><b>S/ {{ r.montoMatricula | number: '1.2-2' }}</b></div>
              <div class="mel-cuenta"><span>Comprobante</span><b>{{ r.comprobante ?? '—' }}</b></div>
              <div class="mel-cuenta"><span>Fecha</span><b>{{ r.pagadoEn }}</b></div>
              <p class="muted" style="font-size: .85rem">Tu vacante queda reservada mientras se revisa.</p>
            </div>
          </div>
         } @else if (r.estado === 'RECHAZADA') {
          <div class="mel-exito" role="status" style="border-color: #f3c4c4; background: #fdeeee">
            <span class="mel-check" aria-hidden="true" style="background: #b42318">!</span>
            <div><h2>La matrícula no fue aprobada</h2><p>{{ r.alumno }} · código <b>{{ r.codigo }}</b>. El colegio se comunicará contigo para devolver el pago.</p></div>
          </div>
          <div class="mel-acc"><button type="button" class="pub-btn pub-btn-primario" (click)="nuevaMatricula()">Hacer otra matrícula</button></div>
         } @else {
          <div class="mel-exito" role="status">
            <span class="mel-check" aria-hidden="true">✓</span>
            <div><h2>¡Matrícula aprobada!</h2><p>{{ r.alumno }} · {{ r.grado }} · sección {{ r.seccion }} ({{ r.turno }}) · código <b>{{ r.codigo }}</b></p></div>
          </div>
          <div class="pub-dos mel-dos">
            <div class="pub-tarjeta">
              <h3>Tu cuenta</h3>
              <dl class="mel-dl">
                <div><dt>Usuario del alumno</dt><dd><code>{{ r.usuarioAlumno ?? 'lo creará secretaría' }}</code></dd></div>
                <div><dt>Usuario del apoderado</dt><dd><code>{{ r.usuarioApoderado ?? 'ya tenías cuenta o la creará secretaría' }}</code></dd></div>
                <div><dt>Clave inicial</dt><dd>El número de documento de cada persona. Al entrar por primera vez el sistema te pedirá cambiarla.</dd></div>
              </dl>
              @if (r.mensajeCuentas) { <div class="mel-aviso">{{ r.mensajeCuentas }}</div> }
              <div class="mel-acc">
                <a class="pub-btn pub-btn-primario" routerLink="/login">Ir al portal</a>
                <button type="button" class="pub-btn pub-btn-borde" (click)="nuevaMatricula()">Hacer otra matrícula</button>
              </div>
            </div>
            <div class="pub-tarjeta">
              <h3>Constancia y correo</h3>
              @if (r.correo === 'ENVIADO') { <div class="pub-ok">Te enviamos la constancia (PDF) a <b>{{ r.correoDestino }}</b> con el horario y las finanzas.</div> }
              @else if (r.correo === 'ERROR') { <div class="mel-aviso">No pudimos enviar el correo. Descarga la constancia aquí.</div> }
              @else { <div class="mel-aviso">No dejaste un correo: descarga tu constancia aquí.</div> }
              <div class="mel-acc">
                <button type="button" class="pub-btn pub-btn-primario" [disabled]="descargando()" (click)="descargar()">{{ descargando() ? 'Preparando…' : 'Descargar constancia (PDF)' }}</button>
                <button type="button" class="pub-btn pub-btn-borde" (click)="imprimir()">Imprimir</button>
              </div>
              <p class="muted" style="font-size: .85rem; margin-top: 12px">Pendiente en secretaría: copia del DNI del alumno, partida de nacimiento y ficha SIAGIE.</p>
            </div>
            <div class="pub-tarjeta">
              <h3>Pago de la matrícula</h3>
              <div class="mel-cuenta"><span>Monto pagado</span><b>S/ {{ r.montoMatricula | number: '1.2-2' }}</b></div>
              <div class="mel-cuenta"><span>Comprobante</span><b>{{ r.comprobante ?? '—' }}</b></div>
              <div class="mel-cuenta"><span>Fecha</span><b>{{ r.pagadoEn }}</b></div>
              <h3 style="margin-top: 18px">Pensiones del año</h3>
              @for (p of r.pensiones; track p.descripcion) { <div class="mel-cuenta"><span>{{ p.descripcion }} · vence {{ p.vence }}</span><b>S/ {{ p.monto | number: '1.2-2' }}</b></div> }
              @empty { <p class="muted">Se mostrarán en tu cuenta apenas se generen.</p> }
              @if (r.pensiones.length > 0) { <div class="mel-cuenta total"><span>Total pensiones</span><b>S/ {{ r.totalPensiones | number: '1.2-2' }}</b></div> }
            </div>
            <div class="pub-tarjeta">
              <h3>Horario de la sección {{ r.seccion }}</h3>
              @if (r.horario.length === 0) { <p class="muted">Todavía no está publicado. Te avisaremos por el portal.</p> }
              @else {
                <div class="mel-horario" role="table" aria-label="Horario semanal">
                  @for (d of dias; track d) {
                    <div role="row"><strong>{{ d }}</strong>
                      @for (c of claseDeDia(r, d); track c.hora) { <span role="cell"><b>{{ c.hora }}</b> {{ c.curso }}</span> }
                    </div>
                  }
                </div>
              }
            </div>
          </div>
         }
        }
      }

      <p class="muted mel-demo">Pasarela de pago de demostración: no se realiza ningún cobro real. Este sitio es de ejemplo.</p>
    </div></section>

    @if (modalPago()) {
      <app-modal titulo="Pago con tarjeta (demostración)" [ancho]="460" (cerrar)="cerrarPago()">
        <div class="mel-demo-caja"><strong>No se cobra nada.</strong> Usa la tarjeta de prueba <code>4242 4242 4242 4242</code>, un vencimiento futuro y cualquier CVV.
          La <code>4000 0000 0000 0002</code> simula un rechazo.</div>
        <div class="mel-monto">Total a pagar <b>S/ {{ (inicio()?.montoMatricula ?? 0) | number: '1.2-2' }}</b></div>
        <form [formGroup]="tarjeta" (ngSubmit)="pagar()" id="form-tarjeta" novalidate>
          <div class="pub-campo"><label for="t-num">Número de tarjeta</label>
            <input id="t-num" class="pub-input" formControlName="numero" inputmode="numeric" autocomplete="cc-number" placeholder="0000 0000 0000 0000" maxlength="23" (input)="formatearNumero($any($event.target))" /></div>
          <div class="pub-campo"><label for="t-tit">Nombre del titular</label><input id="t-tit" class="pub-input" formControlName="titular" autocomplete="cc-name" /></div>
          <div class="mel-fila">
            <div class="pub-campo"><label for="t-ven">Vencimiento</label><input id="t-ven" class="pub-input" formControlName="vencimiento" inputmode="numeric" autocomplete="cc-exp" placeholder="MM/AA" maxlength="5" (input)="formatearVencimiento($any($event.target))" /></div>
            <div class="pub-campo"><label for="t-cvv">CVV</label><input id="t-cvv" type="password" class="pub-input" formControlName="cvv" inputmode="numeric" autocomplete="cc-csc" maxlength="4" /></div>
          </div>
          @if (errorPago()) { <div class="mel-error" role="alert">{{ errorPago() }}</div> }
        </form>
        <ng-container footer>
          <button type="button" class="pub-btn pub-btn-borde" [disabled]="pagando()" (click)="cerrarPago()">Cancelar</button>
          <button type="submit" form="form-tarjeta" class="pub-btn pub-btn-primario" [disabled]="pagando()">{{ pagando() ? 'Procesando… (unos segundos)' : 'Pagar S/ ' + ((inicio()?.montoMatricula ?? 0) | number: '1.2-2') }}</button>
        </ng-container>
      </app-modal>
    }
  `,
  styles: `
    .mel-pasos { list-style: none; display: flex; gap: 8px; padding: 0; margin: 0 0 22px; flex-wrap: wrap; }
    .mel-pasos li { display: flex; align-items: center; gap: 8px; padding: 6px 14px 6px 6px; border-radius: 999px; background: #eef1f8; color: #4b5875; font-weight: 600; font-size: .9rem; }
    .mel-pasos li.on { background: var(--pc, #1f3a87); color: #fff; }
    .mel-pasos li.hecho { background: #ddf3ea; color: #0b5d41; }
    .mel-n { width: 26px; height: 26px; border-radius: 50%; display: grid; place-items: center; background: rgb(255 255 255 / .35); font-size: .8rem; }
    .mel-h { margin: 0 0 14px; }
    .mel-sigue { margin: 0 0 14px; padding-left: 20px; display: grid; gap: 8px; }
    .mel-sigue li.hecho { color: #0b5d41; }
    .mel-sigue li.ahora { font-weight: 700; }
    .mel-grados { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; margin-top: 16px; }
    .mel-grado { display: flex; flex-direction: column; gap: 4px; text-align: left; padding: 14px; border: 2px solid #dfe5f2; border-radius: 14px; background: #fff; font: inherit; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
    .mel-grado:hover:not(:disabled) { border-color: var(--pc, #1f3a87); }
    .mel-grado.sel { border-color: var(--pc, #1f3a87); box-shadow: 0 0 0 3px rgb(31 58 135 / .15); }
    .mel-grado:disabled { opacity: .55; cursor: not-allowed; }
    .mel-grado strong { font-size: 1.05rem; }
    .mel-vac { font-size: .8rem; font-weight: 700; color: #0b5d41; }
    .mel-vac.poco { color: #8a4b08; }
    .mel-acc { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
    .mel-dos { grid-template-columns: 1fr 1fr; gap: 18px; align-items: start; }
    .mel-fila { display: flex; gap: 10px; }
    .mel-fila > * { flex: 1; min-width: 0; }
    .mel-corto { flex: 0 0 110px !important; }
    fieldset.pub-tarjeta { margin: 0; }
    fieldset legend { font-weight: 800; padding: 0 6px; color: var(--pc, #1f3a87); }
    .mel-msg, .mel-error { color: #b42318; font-size: .85rem; }
    .mel-error { background: #fde3e0; padding: 10px 12px; border-radius: 10px; margin-top: 12px; }
    .mel-aviso { background: #fff1d6; color: #8a4b08; padding: 10px 12px; border-radius: 10px; margin: 10px 0; }
    .mel-trampa { position: absolute; left: -9999px; width: 1px; height: 1px; overflow: hidden; }
    .mel-dl { display: grid; gap: 8px; margin: 0 0 12px; }
    .mel-dl div { display: grid; gap: 2px; }
    .mel-dl dt { font-size: .78rem; color: #6b7694; text-transform: uppercase; letter-spacing: .04em; }
    .mel-dl dd { margin: 0; font-weight: 600; }
    .mel-cuenta { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid #eef1f8; }
    .mel-cuenta.total { border-bottom: 0; font-size: 1.05rem; }
    .mel-exito { display: flex; align-items: center; gap: 16px; padding: 18px 20px; margin-bottom: 18px; border-radius: 16px; background: #ddf3ea; color: #0b5d41; }
    .mel-exito h2, .mel-exito p { margin: 0; }
    .mel-check { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; background: #0b5d41; color: #fff; font-size: 1.6rem; flex: none; }
    .mel-horario { display: grid; gap: 8px; }
    .mel-horario div[role=row] { display: grid; grid-template-columns: 90px 1fr; gap: 4px 10px; padding: 6px 0; border-bottom: 1px solid #eef1f8; font-size: .88rem; }
    .mel-horario strong { grid-row: span 8; }
    .mel-horario span { display: block; }
    .mel-demo { margin-top: 22px; font-size: .85rem; }
    .mel-demo-caja { background: #eef3ff; border: 1px solid #c9d6ff; border-radius: 10px; padding: 10px 12px; font-size: .88rem; margin-bottom: 12px; }
    .mel-monto { display: flex; justify-content: space-between; font-size: 1.1rem; margin-bottom: 14px; }
    @media (max-width: 800px) { .mel-dos { grid-template-columns: 1fr; } .mel-fila { flex-direction: column; } .mel-corto { flex: 1 1 auto !important; } }
    @media print { .pub-cab, .pub-pie, .pub-demo, .mel-acc, .mel-pasos { display: none !important; } }
  `
})
export class MatriculaEnLineaComponent implements OnInit {
  private readonly api = inject(MatriculaPublicaService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly pasos: { id: Paso; titulo: string }[] = [
    { id: 'grado', titulo: 'Grado' }, { id: 'datos', titulo: 'Datos' }, { id: 'pago', titulo: 'Pago' }, { id: 'listo', titulo: 'Revisión' }
  ];
  protected readonly parentescos = PARENTESCOS;
  protected readonly dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
  protected readonly hoy = new Date().toISOString().slice(0, 10);

  protected readonly oferta = signal<OfertaMatricula | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly paso = signal<Paso>('grado');
  protected readonly nivelId = signal(0);
  protected readonly gradoId = signal(0);
  protected readonly acepto = signal(false);
  protected readonly inicio = signal<InicioMatricula | null>(null);
  protected readonly resumen = signal<ResumenMatricula | null>(null);
  protected readonly trabajando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly modalPago = signal(false);
  protected readonly pagando = signal(false);
  protected readonly errorPago = signal<string | null>(null);
  protected readonly descargando = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    alumno: this.fb.nonNullable.group({
      tipoDocumento: 'DNI', numeroDocumento: ['', [Validators.required, documentoValido]], nombres: ['', [Validators.required, Validators.maxLength(100)]],
      apellidoPaterno: ['', [Validators.required, Validators.maxLength(80)]], apellidoMaterno: [''], fechaNacimiento: ['', Validators.required], sexo: 'F'
    }),
    apoderado: this.fb.nonNullable.group({
      tipoDocumento: 'DNI', numeroDocumento: ['', [Validators.required, documentoValido]], nombres: ['', [Validators.required, Validators.maxLength(100)]],
      apellidos: ['', [Validators.required, Validators.maxLength(150)]], telefono: ['', [Validators.required, Validators.pattern(/^[0-9+() -]{6,20}$/)]],
      email: ['', Validators.email], parentesco: 'MADRE'
    }),
    sitio: ''
  });
  protected readonly tarjeta = this.fb.nonNullable.group({ numero: ['', Validators.required], titular: ['', Validators.required], vencimiento: ['', Validators.required], cvv: ['', Validators.required] });

  protected get f() { return this.form.controls; }
  protected readonly indice = computed(() => this.pasos.findIndex(p => p.id === this.paso()));
  protected readonly gradosDelNivel = computed(() => this.oferta()?.niveles.find(n => n.id === this.nivelId())?.grados ?? []);
  protected readonly gradoActual = computed(() => this.oferta()?.niveles.flatMap(n => n.grados).find(g => g.id === this.gradoId()) ?? null);

  ngOnInit(): void {
    this.api.oferta().subscribe({
      next: o => {
        this.oferta.set(o);
        this.nivelId.set(o.niveles[0]?.id ?? 0);
        this.cargando.set(false);
        this.retomar();
      },
      error: () => {
        this.cargando.set(false);
        this.errorCarga.set('No pudimos cargar las vacantes en este momento. Intenta de nuevo en unos minutos.');
      }
    });
  }

  protected elegirGrado(g: GradoOferta): void {
    this.gradoId.set(g.id);
  }

  /**
   * Empieza una matrícula nueva: olvida el trámite guardado (se guarda en la sesión de esta pestaña, no en la caché del navegador, por eso
   * «borrar caché» no lo quita) y deja los pasos y formularios en blanco.
   */
  protected nuevaMatricula(): void {
    try { sessionStorage.removeItem(CLAVE); } catch { /* nada que borrar */ }
    this.resumen.set(null);
    this.inicio.set(null);
    this.gradoId.set(0);
    this.nivelId.set(this.oferta()?.niveles[0]?.id ?? 0);
    this.acepto.set(false);
    this.error.set(null);
    this.errorPago.set(null);
    this.modalPago.set(false);
    this.form.reset({ alumno: { tipoDocumento: 'DNI', sexo: 'F' }, apoderado: { tipoDocumento: 'DNI', parentesco: 'MADRE' }, sitio: '' });
    this.tarjeta.reset();
    this.paso.set('grado');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected ir(p: Paso): void {
    if (p === 'pago' && !this.validarDatos()) return;
    this.error.set(null);
    this.paso.set(p);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  private validarDatos(): boolean {
    this.form.markAllAsTouched();
    return this.form.valid;
  }

  protected malo(ruta: string): boolean {
    const c = this.form.get(ruta);
    return !!c && c.invalid && (c.touched || c.dirty);
  }

  protected mensajeDoc(grupo: 'alumno' | 'apoderado'): string {
    return this.form.get(`${grupo}.tipoDocumento`)?.value === 'DNI' ? 'El DNI tiene 8 números.' : 'El documento admite letras y números (6 a 20).';
  }

  // --- reservar y pagar ---
  protected reservarYPagar(): void {
    if (this.inicio()) {
      this.abrirPago();
      return;
    }
    const v = this.form.getRawValue();
    this.trabajando.set(true);
    this.error.set(null);
    this.api.iniciar({
      gradoId: this.gradoId(),
      alumno: { ...v.alumno, numeroDocumento: v.alumno.numeroDocumento.trim() },
      apoderado: { ...v.apoderado, numeroDocumento: v.apoderado.numeroDocumento.trim(), email: v.apoderado.email.trim() || null },
      acepto: this.acepto(),
      sitio: v.sitio
    }).subscribe({
      next: i => {
        this.trabajando.set(false);
        this.inicio.set(i);
        try { sessionStorage.setItem(CLAVE, JSON.stringify({ referencia: i.referencia, token: i.token })); } catch { /* sin almacenamiento: no se puede retomar */ }
        this.abrirPago();
      },
      error: e => {
        this.trabajando.set(false);
        this.error.set(this.mensaje(e, 'No pudimos reservar la vacante. Intenta de nuevo.'));
      }
    });
  }

  private abrirPago(): void {
    this.tarjeta.reset({ numero: '', titular: '', vencimiento: '', cvv: '' });
    this.errorPago.set(null);
    this.modalPago.set(true);
  }

  protected cerrarPago(): void {
    if (!this.pagando()) this.modalPago.set(false);
  }

  protected pagar(): void {
    const i = this.inicio();
    if (!i) return;
    if (this.tarjeta.invalid) {
      this.errorPago.set('Completa todos los datos de la tarjeta.');
      return;
    }
    this.pagando.set(true);
    this.errorPago.set(null);
    this.api.pagar(i.referencia, i.token, this.tarjeta.getRawValue()).subscribe({
      next: r => {
        this.pagando.set(false);
        this.modalPago.set(false);
        this.resumen.set(r);
        this.paso.set('listo');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: e => {
        this.pagando.set(false);
        this.errorPago.set(this.mensaje(e, 'No pudimos procesar el pago. No se hizo ningún cobro.'));
      }
    });
  }

  /** Si la persona recarga la página en medio del trámite, se retoma donde estaba. */
  private retomar(): void {
    let guardado: { referencia: string; token: string } | null = null;
    try { guardado = JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null'); } catch { guardado = null; }
    if (!guardado) return;
    this.api.resumen(guardado.referencia, guardado.token).subscribe({
      next: r => {
        this.resumen.set(r);
        if (r.estado !== 'INICIADA') {
          this.paso.set('listo');
        } else {
          this.inicio.set({ referencia: r.referencia, token: guardado!.token, codigo: r.codigo, alumno: r.alumno, grado: r.grado, seccion: r.seccion, turno: r.turno, montoMatricula: r.montoMatricula ?? 0, vencePago: null });
          this.paso.set('pago');
        }
      },
      error: () => { try { sessionStorage.removeItem(CLAVE); } catch { /* nada */ } }
    });
  }

  protected readonly actualizando = signal(false);

  /** Vuelve a consultar el estado del trámite (por si ya fue aprobado). */
  protected actualizarEstado(): void {
    const r = this.resumen();
    const i = this.inicio() ?? this.tokenGuardado();
    if (!r || !i) return;
    this.actualizando.set(true);
    this.api.resumen(r.referencia, i.token).subscribe({
      next: nuevo => { this.resumen.set(nuevo); this.actualizando.set(false); },
      error: () => this.actualizando.set(false)
    });
  }

  // --- constancia ---
  protected descargar(): void {
    const r = this.resumen();
    const i = this.inicio() ?? this.tokenGuardado();
    if (!r || !i) return;
    this.descargando.set(true);
    this.api.constancia(r.referencia, i.token).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Constancia-matricula-${r.codigo}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.descargando.set(false);
      },
      error: () => this.descargando.set(false)
    });
  }

  private tokenGuardado(): { token: string } | null {
    try { return JSON.parse(sessionStorage.getItem(CLAVE) ?? 'null'); } catch { return null; }
  }

  protected imprimir(): void {
    window.print();
  }

  protected claseDeDia(r: ResumenMatricula, dia: string) {
    return r.horario.filter(c => c.dia === dia);
  }

  protected formatearNumero(el: HTMLInputElement): void {
    const d = el.value.replace(/\D/g, '').slice(0, 19);
    el.value = d.replace(/(.{4})/g, '$1 ').trim();
    this.tarjeta.controls.numero.setValue(el.value, { emitEvent: false });
  }

  protected formatearVencimiento(el: HTMLInputElement): void {
    const d = el.value.replace(/\D/g, '').slice(0, 4);
    el.value = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
    this.tarjeta.controls.vencimiento.setValue(el.value, { emitEvent: false });
  }

  private mensaje(e: unknown, defecto: string): string {
    const h = e as HttpErrorResponse;
    const detalles = h?.error?.detalles;
    if (Array.isArray(detalles) && detalles.length > 0) return detalles.slice(0, 3).join('. ') + '.';
    const m = h?.error?.mensaje;
    return typeof m === 'string' && m ? m : h?.status === 429 ? 'Demasiados intentos seguidos. Espera un momento e inténtalo de nuevo.' : defecto;
  }
}
