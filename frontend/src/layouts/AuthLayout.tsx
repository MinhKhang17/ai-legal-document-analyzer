import { CheckCircle2, Gavel, ShieldCheck } from 'lucide-react';
import { Outlet } from 'react-router-dom';
import { LanguageToggle } from '../components/common/LanguageToggle';
import { ThemeToggle } from '../components/common/ThemeToggle';
import { useI18n } from '../hooks/useI18n';

export function AuthLayout() {
  const { t } = useI18n();
  const heroFeatures = [
    t('authHero.features.clauseComparison'),
    t('authHero.features.riskModeling'),
    t('authHero.features.multiJurisdiction'),
  ];

  return (
    <div className="h-screen overflow-hidden bg-surface text-on-surface dark:bg-slate-950 dark:text-slate-100 lg:grid lg:grid-cols-[0.92fr_1fr]">
      <aside className="relative hidden h-screen overflow-hidden bg-primary px-8 py-6 text-white lg:block">
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              'radial-gradient(circle at 2px 2px, #ffffff 1px, transparent 0)',
            backgroundSize: '40px 40px',
          }}
        />

        <div className="relative z-10 flex h-full items-center justify-center">
          <div className="flex h-full max-h-[790px] w-full max-w-[600px] flex-col justify-center">
            <div className="shrink-0">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-secondary shadow-lg">
                  <Gavel className="h-5 w-5 text-white" aria-hidden="true" />
                </div>

                <div>
                  <h1 className="font-sans text-[17px] font-bold leading-none text-white">
                    {t('app.name')}
                  </h1>
                  <p className="mt-1 text-[8px] font-bold uppercase tracking-[0.16em] text-secondary-container">
                    {t('app.suite')}
                  </p>
                </div>
              </div>

              <h2 className="mt-6 max-w-[620px] font-sans text-[40px] font-bold leading-[1.08] text-secondary-container">
                {t('authHero.title')}
              </h2>

              <p className="mt-4 max-w-[520px] text-[11px] leading-5 text-slate-200">
                {t('authHero.description')}
              </p>

              <div className="mt-5 w-full rounded-lg border border-white/10 bg-white/5 p-3">
                <div className="flex items-start gap-2.5">
                  <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-secondary-container" />
                  <div>
                    <p className="font-sans text-[16px] font-bold text-white">
                      {t('authHero.privacyTitle')}
                    </p>
                    <p className="mt-1 text-[9px] leading-4 text-slate-200">
                      {t('authHero.privacyDescriptionLine1')}
                      <br />
                      {t('authHero.privacyDescriptionLine2')}
                    </p>
                  </div>
                </div>
              </div>

              <div className="mt-4 w-full space-y-1.5">
                {heroFeatures.map((item) => (
                  <div key={item} className="flex items-center gap-2.5">
                    <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-secondary-container" />
                    <span className="text-[10px] font-medium text-white">
                      {item}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-5 min-h-0 flex-1">
              <div className="h-full max-h-[310px] min-h-[220px] w-full overflow-hidden rounded-lg border border-white/20 bg-[#071d2f] shadow-2xl">
                <img
                  src="https://lh3.googleusercontent.com/aida-public/AB6AXuBnV7yVgFP_Jv-w1Mx2F1tVanluV7CuMqynzlMzzTpHHZH4vu5OZYUUbpP4NIqxEUEnadHufTbH7iruGOVyn6BUTvYwsviWW6exTVaXBTfnclN1xnngiR9fleBRcZmzW39yGJ2h6lCTO9_u4ag94K3owtyWn1sFKc4Ub6ChriHIDuLG3Tqb7Lz5qSG7tGyynncfb1CogRa3DHy4V6r6AOuxuhYBVi0NGX_JXpp6xpU2Cd0OQPYme_sGJU3G35f366S23n7ryNWaNwY"
                  alt={t('auth.workspaceIllustrationAlt')}
                  className="h-full w-full object-cover object-[center_45%]"
                />
              </div>
            </div>
          </div>
        </div>
      </aside>

      <main className="relative flex h-screen w-full items-center justify-center overflow-y-auto bg-[#f5f7fb] px-6 py-8 dark:bg-slate-950 lg:overflow-hidden">
        <div className="absolute right-6 top-6 z-10 flex items-center gap-3 lg:right-8 lg:top-7">
          <LanguageToggle />
          <ThemeToggle />
        </div>

        <div className="w-full max-w-[400px]">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
