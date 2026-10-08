import { Component, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-ai-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div id="ai-chat">
      <div id="ai-window" [class.open]="open" *ngIf="open">
        <div className="ai-header">
          <span className="ai-avatar">🌊</span>
          <div className="ai-header-info">
            <span className="ai-name">Sofía · Concierge</span>
            <span className="ai-status">En línea</span>
          </div>
          <button id="ai-header-close" (click)="open = false" aria-label="Cerrar chat">×</button>
        </div>
        <div id="ai-messages" #listRef>
          <div 
            *ngFor="let m of messages" 
            className="ai-msg"
            [class.bot]="m.role === 'assistant'"
            [class.user]="m.role === 'user'"
          >
            {{ m.content }}
          </div>
          <div className="ai-msg bot ai-typing" *ngIf="sending">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div className="ai-quick-btns" *ngIf="messages.length < 3">
          <button className="ai-quick" *ngFor="let q of quickReplies" (click)="send(q)">{{ q }}</button>
        </div>
        <div id="ai-input-area">
          <input
            id="ai-input"
            placeholder="Escribe tu mensaje…"
            [(ngModel)]="input"
            (keydown.enter)="send(input)"
          />
          <button id="ai-send" (click)="send(input)" [disabled]="sending">➤</button>
        </div>
      </div>
      <button id="ai-toggle" (click)="open = !open" aria-label="Abrir chat">
        <span className="ai-dot"></span>
        {{ open ? 'Cerrar chat' : 'Habla con Sofía' }}
      </button>
    </div>
  `
})
export class AIChatWidgetComponent {
  open = false;
  messages = [
    { role: 'assistant', content: '¡Hola! 👋 Soy Sofía, tu concierge virtual en Palacio del Mar. ¿En qué puedo ayudarte?' }
  ];
  input = '';
  sending = false;
  quickReplies = ['¿Qué suite me recomiendas?', '¿Qué experiencias hay?', '¿Hay disponibilidad?'];

  @ViewChild('listRef') listRef!: ElementRef;

  constructor(private api: ApiService) {}

  send(text: string): void {
    const clean = text.trim();
    if (!clean || this.sending) return;

    this.messages.push({ role: 'user', content: clean });
    this.input = '';
    this.sending = true;

    this.api.post<any>('/chat', { message: clean, history: this.messages.slice(-6) }).subscribe({
      next: (res) => {
        this.messages.push({ role: 'assistant', content: res.reply || '¡Con gusto te ayudo!' });
        this.sending = false;
      },
      error: () => {
        this.messages.push({ role: 'assistant', content: 'En este momento estoy atendiendo varias solicitudes. ¡Escríbenos por WhatsApp para atención inmediata!' });
        this.sending = false;
      }
    });
  }
}
