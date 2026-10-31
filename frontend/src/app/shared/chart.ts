import { afterRenderEffect, Component, DestroyRef, ElementRef, inject, input, viewChild } from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';

// Chart.js n'est importé que par les écrans qui l'utilisent : il reste hors du bundle initial.
Chart.register(...registerables);
Chart.defaults.font.family = 'Roboto, "Helvetica Neue", sans-serif';
Chart.defaults.color = '#46464f';

/** Rôles de couleur des graphes (méthode dataviz : une série = slot 1, grille et axes discrets). */
export const VIZ = {
  series: '#2a78d6',
  seriesWash: 'rgba(42, 120, 214, 0.10)',
  grid: '#e4e4ea',
  textPrimary: '#1b1b21',
  textSecondary: '#46464f',
};

/** Canvas Chart.js recréé quand la configuration change. */
@Component({
  selector: 'app-chart',
  template: `
    <div class="chart-box" [style.height.px]="height()">
      <canvas #canvas role="img" [attr.aria-label]="label()"></canvas>
    </div>
  `,
  styles: `.chart-box { position: relative; width: 100%; }`,
})
export class ChartCanvas {
  readonly config = input.required<ChartConfiguration>();
  readonly label = input.required<string>();
  readonly height = input(260);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    afterRenderEffect(() => {
      const config = this.config();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, config);
    });
    inject(DestroyRef).onDestroy(() => this.chart?.destroy());
  }
}
