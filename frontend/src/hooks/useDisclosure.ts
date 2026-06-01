import { useCallback, useState } from 'react';

export const useDisclosure = (initialOpen = false) => {
  const [open, setOpen] = useState(initialOpen);
  const onOpen = useCallback(() => setOpen(true), []);
  const onClose = useCallback(() => setOpen(false), []);
  const onToggle = useCallback(() => setOpen((previous) => !previous), []);
  return { open, setOpen, onOpen, onClose, onToggle };
};
