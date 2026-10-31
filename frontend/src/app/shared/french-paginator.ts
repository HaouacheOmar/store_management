import { MatPaginatorIntl } from '@angular/material/paginator';

export function frenchPaginator(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Par page';
  intl.nextPageLabel = 'Page suivante';
  intl.previousPageLabel = 'Page précédente';
  intl.firstPageLabel = 'Première page';
  intl.lastPageLabel = 'Dernière page';
  intl.getRangeLabel = (page, size, length) =>
    length === 0 ? '0 sur 0' : `${page * size + 1} – ${Math.min((page + 1) * size, length)} sur ${length}`;
  return intl;
}
