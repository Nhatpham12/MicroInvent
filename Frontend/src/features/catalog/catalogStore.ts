import { create } from 'zustand'
import { newCatalogRows } from './catalogData'
import type { CatalogKind, CatalogRow } from './catalogData'

type CatalogStore = {
  rowsByKind: Record<CatalogKind, CatalogRow[]>
  addRow: (kind: CatalogKind, row: CatalogRow) => void
  updateRow: (kind: CatalogKind, row: CatalogRow) => void
}

export const useCatalogStore = create<CatalogStore>((set) => ({
  rowsByKind: newCatalogRows(),
  addRow: (kind, row) =>
    set((state) => ({
      rowsByKind: {
        ...state.rowsByKind,
        [kind]: [row, ...state.rowsByKind[kind]],
      },
    })),
  updateRow: (kind, row) =>
    set((state) => ({
      rowsByKind: {
        ...state.rowsByKind,
        [kind]: state.rowsByKind[kind].map((current) =>
          current.id === row.id ? row : current,
        ),
      },
    })),
}))
