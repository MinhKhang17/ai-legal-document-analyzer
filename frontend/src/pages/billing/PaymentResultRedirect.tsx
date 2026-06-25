import { Navigate, useLocation } from "react-router-dom";

export function PaymentResultRedirect() {
  const location = useLocation();

  return <Navigate to={`/billing/payment-result${location.search}`} replace />;
}