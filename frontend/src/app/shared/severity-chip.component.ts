import { Component, input } from '@angular/core';

@Component({
  selector: 'app-severity-chip',
  standalone: true,
  template: `<span class="chip" [class]="'chip ' + severity()">{{ severity() }}</span>`,
})
export class SeverityChipComponent {
  readonly severity = input.required<string>();
}
